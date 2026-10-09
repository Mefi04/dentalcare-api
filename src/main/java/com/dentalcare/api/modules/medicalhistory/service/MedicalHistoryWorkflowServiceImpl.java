package com.dentalcare.api.modules.medicalhistory.service;

import com.dentalcare.api.exception.*;
import com.dentalcare.api.modules.audit.service.*;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDocument;
import com.dentalcare.api.modules.clinicalrecords.repository.ClinicalDocumentRepository;
import com.dentalcare.api.modules.medicalhistory.dto.request.*;
import com.dentalcare.api.modules.medicalhistory.dto.response.*;
import com.dentalcare.api.modules.medicalhistory.model.*;
import com.dentalcare.api.modules.medicalhistory.repository.*;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.dto.response.PatientHealthStatus;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import org.springframework.dao.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MedicalHistoryWorkflowServiceImpl implements MedicalHistoryWorkflowService {
    private static final int MAX_PAGE_SIZE=100;
    private static final Sort QUESTIONNAIRE_ORDER=Sort.by(Sort.Order.desc("updatedAt"),Sort.Order.desc("id"));
    private static final Sort VERSION_ORDER=Sort.by(Sort.Order.desc("versionNumber"));
    private static final Set<String> BOOLEAN_VALUES=Set.of("YES","NO","UNKNOWN","NOT_APPLICABLE");
    private final MedicalHistoryTemplateRepository templateRepository;
    private final MedicalHistoryTemplateVersionRepository templateVersionRepository;
    private final MedicalHistoryQuestionnaireRepository questionnaireRepository;
    private final MedicalHistoryAnswerRevisionRepository revisionRepository;
    private final MedicalHistoryReviewNoteRepository noteRepository;
    private final MedicalHistoryAttestationRepository attestationRepository;
    private final MedicalHistoryVersionRepository versionRepository;
    private final MedicalHistoryTransitionEventRepository transitionRepository;
    private final MedicalHistoryRepository medicalHistoryRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final ClinicalDocumentRepository documentRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public MedicalHistoryWorkflowServiceImpl(MedicalHistoryTemplateRepository templateRepository,
            MedicalHistoryTemplateVersionRepository templateVersionRepository,
            MedicalHistoryQuestionnaireRepository questionnaireRepository,
            MedicalHistoryAnswerRevisionRepository revisionRepository,
            MedicalHistoryReviewNoteRepository noteRepository,
            MedicalHistoryAttestationRepository attestationRepository,
            MedicalHistoryVersionRepository versionRepository,
            MedicalHistoryTransitionEventRepository transitionRepository,
            MedicalHistoryRepository medicalHistoryRepository,PatientRepository patientRepository,
            UserRepository userRepository,ClinicalDocumentRepository documentRepository,
            AuditService auditService,ObjectMapper objectMapper,Clock clock) {
        this.templateRepository=templateRepository;this.templateVersionRepository=templateVersionRepository;
        this.questionnaireRepository=questionnaireRepository;this.revisionRepository=revisionRepository;
        this.noteRepository=noteRepository;this.attestationRepository=attestationRepository;
        this.versionRepository=versionRepository;this.transitionRepository=transitionRepository;
        this.medicalHistoryRepository=medicalHistoryRepository;this.patientRepository=patientRepository;
        this.userRepository=userRepository;this.documentRepository=documentRepository;
        this.auditService=auditService;this.objectMapper=objectMapper;this.clock=clock;
    }

    @Override @Transactional
    public MedicalHistoryResponse importLegacy(UUID patientId,UpdateMedicalHistoryRequest request,UUID actorId){
        if(request==null)throw new BadRequestException("Medical history is required");
        Patient patient=patientRepository.findByIdForUpdate(requireId(patientId,"Patient id is required")).orElseThrow(()->new ResourceNotFoundException("Patient not found"));
        User dentist=actor(actorId);Instant now=clock.instant();
        Map<String,Object> snapshot=new LinkedHashMap<>();snapshot.put("allergies",normalizeLegacy(request.allergies(),"Allergies"));snapshot.put("currentMedications",normalizeLegacy(request.currentMedications(),"Current medications"));snapshot.put("relevantConditions",normalizeLegacy(request.relevantConditions(),"Relevant conditions"));snapshot.put("observations",optional(request.observations(),4000));String serialized=json(snapshot);
        MedicalHistoryVersion previous=versionRepository.findCurrentForUpdate(patientId).orElse(null);if(previous!=null)previous.supersede();int number=(int)versionRepository.countByPatient_Id(patientId)+1;
        MedicalHistoryVersion version=versionRepository.saveAndFlush(new MedicalHistoryVersion(UUID.randomUUID(),patient,number,MedicalHistoryVersionSource.LEGACY_COMPATIBILITY,null,null,previous,dentist,now,serialized,true));
        syncLegacyProjection(patient,serialized,now,version);auditService.success("MEDICAL_HISTORY_LEGACY_IMPORTED","MEDICAL_HISTORY","MedicalHistory",patientId,actorId);
        MedicalHistory history=medicalHistoryRepository.findByPatient_Id(patientId).orElseThrow();
        return new MedicalHistoryResponse(patientId,List.copyOf(history.getAllergies()),List.copyOf(history.getCurrentMedications()),List.copyOf(history.getRelevantConditions()),history.getObservations(),history.getCreatedAt(),history.getUpdatedAt(),PatientHealthStatus.UPDATED);
    }

    @Override @Transactional
    public MedicalHistoryTemplateResponse createTemplate(CreateMedicalHistoryTemplateRequest request,UUID actorId) {
        User actor=actor(actorId); Instant now=clock.instant(); String code=text(request.code(),80).toUpperCase(Locale.ROOT);
        if(templateRepository.findByCodeIgnoreCase(code).isPresent()) throw new ConflictException("Medical history template code already exists");
        validateTemplateRequest(request);
        MedicalHistoryTemplate template=templateRepository.save(new MedicalHistoryTemplate(UUID.randomUUID(),code,text(request.name(),150),now));
        MedicalHistoryTemplateVersion version=new MedicalHistoryTemplateVersion(UUID.randomUUID(),template,1,text(request.title(),180),actor,now);
        int sectionOrder=0;
        for(var sectionRequest:request.sections()){
            var section=new MedicalHistoryTemplateSection(UUID.randomUUID(),version,text(sectionRequest.key(),80).toUpperCase(Locale.ROOT),text(sectionRequest.title(),180),optional(sectionRequest.description(),500),sectionOrder++);
            int questionOrder=0;
            for(var questionRequest:sectionRequest.questions()){
                String choices=questionRequest.choices()==null?null:json(questionRequest.choices());
                String conditional=questionRequest.conditionalNoteRule()==null?null:json(Map.of("equals",text(questionRequest.conditionalNoteRule(),1000)));
                section.addQuestion(new MedicalHistoryTemplateQuestion(UUID.randomUUID(),section,text(questionRequest.key(),100).toUpperCase(Locale.ROOT),text(questionRequest.prompt(),500),questionRequest.answerType(),questionOrder++,questionRequest.required(),questionRequest.notesAllowed(),questionRequest.maxLength(),questionRequest.minValue(),questionRequest.maxValue(),choices,conditional));
            }
            version.addSection(section);
        }
        version=templateVersionRepository.saveAndFlush(version);
        auditService.success("MEDICAL_HISTORY_TEMPLATE_CREATED","MEDICAL_HISTORY","MedicalHistoryTemplateVersion",version.getId(),actorId);
        return templateResponse(version);
    }

    @Override @Transactional
    public MedicalHistoryTemplateResponse createTemplateVersion(UUID templateId,CreateMedicalHistoryTemplateVersionRequest request,UUID actorId){
        if(request==null)throw new BadRequestException("Template version is required");
        MedicalHistoryTemplate template=templateRepository.findById(requireId(templateId,"Template id is required")).orElseThrow(()->new ResourceNotFoundException("Medical history template not found"));
        validateSections(request.sections());User actor=actor(actorId);Instant now=clock.instant();int number=templateVersionRepository.countByTemplate_Id(templateId)+1;
        MedicalHistoryTemplateVersion version=new MedicalHistoryTemplateVersion(UUID.randomUUID(),template,number,text(request.title(),180),actor,now);int sectionOrder=0;
        for(var sectionRequest:request.sections()){
            var section=new MedicalHistoryTemplateSection(UUID.randomUUID(),version,text(sectionRequest.key(),80).toUpperCase(Locale.ROOT),text(sectionRequest.title(),180),optional(sectionRequest.description(),500),sectionOrder++);int questionOrder=0;
            for(var questionRequest:sectionRequest.questions()){
                String choices=questionRequest.choices()==null?null:json(questionRequest.choices());String conditional=questionRequest.conditionalNoteRule()==null?null:json(Map.of("equals",text(questionRequest.conditionalNoteRule(),1000)));
                section.addQuestion(new MedicalHistoryTemplateQuestion(UUID.randomUUID(),section,text(questionRequest.key(),100).toUpperCase(Locale.ROOT),text(questionRequest.prompt(),500),questionRequest.answerType(),questionOrder++,questionRequest.required(),questionRequest.notesAllowed(),questionRequest.maxLength(),questionRequest.minValue(),questionRequest.maxValue(),choices,conditional));
            }version.addSection(section);
        }
        version=templateVersionRepository.saveAndFlush(version);auditService.success("MEDICAL_HISTORY_TEMPLATE_VERSION_CREATED","MEDICAL_HISTORY","MedicalHistoryTemplateVersion",version.getId(),actorId);return templateResponse(version);
    }

    @Override @Transactional
    public MedicalHistoryTemplateResponse updateTemplateVersion(UUID versionId,CreateMedicalHistoryTemplateVersionRequest request,UUID actorId){
        if(request==null)throw new BadRequestException("Template version is required");
        validateSections(request.sections());actor(actorId);
        MedicalHistoryTemplateVersion version=templateVersionRepository.findDetailedById(requireId(versionId,"Template version id is required")).orElseThrow(()->new ResourceNotFoundException("Medical history template version not found"));
        if(version.getStatus()!=MedicalHistoryTemplateStatus.DRAFT)throw new ConflictException("Published template versions are immutable");
        List<MedicalHistoryTemplateSection> replacement=new ArrayList<>();int sectionOrder=0;
        for(var sectionRequest:request.sections()){
            var section=new MedicalHistoryTemplateSection(UUID.randomUUID(),version,text(sectionRequest.key(),80).toUpperCase(Locale.ROOT),text(sectionRequest.title(),180),optional(sectionRequest.description(),500),sectionOrder++);int questionOrder=0;
            for(var questionRequest:sectionRequest.questions()){
                String choices=questionRequest.choices()==null?null:json(questionRequest.choices());String conditional=questionRequest.conditionalNoteRule()==null?null:json(Map.of("equals",text(questionRequest.conditionalNoteRule(),1000)));
                section.addQuestion(new MedicalHistoryTemplateQuestion(UUID.randomUUID(),section,text(questionRequest.key(),100).toUpperCase(Locale.ROOT),text(questionRequest.prompt(),500),questionRequest.answerType(),questionOrder++,questionRequest.required(),questionRequest.notesAllowed(),questionRequest.maxLength(),questionRequest.minValue(),questionRequest.maxValue(),choices,conditional));
            }
            replacement.add(section);
        }
        String title=text(request.title(),180);version.replaceDraftContent(title,List.of());templateVersionRepository.saveAndFlush(version);version.replaceDraftContent(title,replacement);version=templateVersionRepository.saveAndFlush(version);auditService.success("MEDICAL_HISTORY_TEMPLATE_VERSION_UPDATED","MEDICAL_HISTORY","MedicalHistoryTemplateVersion",version.getId(),actorId);return templateResponse(version);
    }

    @Override @Transactional
    public MedicalHistoryTemplateResponse publishTemplate(UUID versionId,UUID actorId){
        User actor=actor(actorId); MedicalHistoryTemplateVersion version=templateVersionRepository.findDetailedById(requireId(versionId,"Template version id is required")).orElseThrow(()->new ResourceNotFoundException("Medical history template version not found"));
        if(version.getStatus()==MedicalHistoryTemplateStatus.PUBLISHED)return templateResponse(version);
        if(version.getStatus()!=MedicalHistoryTemplateStatus.DRAFT)throw new ConflictException("Only a draft template version can be published");
        for(var published:templateVersionRepository.findByTemplate_IdAndStatus(version.getTemplate().getId(),MedicalHistoryTemplateStatus.PUBLISHED)){published.retire();templateVersionRepository.saveAndFlush(published);}
        version.publish(actor,clock.instant()); version=templateVersionRepository.saveAndFlush(version);
        auditService.success("MEDICAL_HISTORY_TEMPLATE_PUBLISHED","MEDICAL_HISTORY","MedicalHistoryTemplateVersion",version.getId(),actorId);
        return templateResponse(version);
    }

    @Override @Transactional(readOnly=true) public MedicalHistoryTemplateResponse getTemplate(UUID versionId){return templateResponse(templateVersionRepository.findDetailedById(requireId(versionId,"Template version id is required")).orElseThrow(()->new ResourceNotFoundException("Medical history template version not found")));}
    @Override @Transactional(readOnly=true) public MedicalHistoryTemplateResponse getCurrentTemplate(){return templateResponse(currentTemplate());}

    @Override @Transactional
    public MedicalHistoryQuestionnaireResponse assign(UUID patientId,AssignMedicalHistoryQuestionnaireRequest request,UUID actorId){
        if(request==null)throw new BadRequestException("Questionnaire assignment is required");
        Patient patient=patient(patientId);User actor=actor(actorId);Instant now=clock.instant();validateExpiry(request.expiresAt(),now);
        ClinicalDocument scan=document(patient.getId(),request.scanDocumentId(),request.scanDocumentId()!=null);
        if(scan!=null && request.source()!=QuestionnaireSource.PAPER && request.source()!=QuestionnaireSource.TRANSCRIPTION)throw new BadRequestException("A scan can only be linked to a paper or transcription questionnaire");
        var q=new MedicalHistoryQuestionnaire(UUID.randomUUID(),patient,currentTemplate(),QuestionnairePurpose.INITIAL,request.source(),null,scan,request.expiresAt(),now);
        q=questionnaireRepository.saveAndFlush(q);transition(q,null,QuestionnaireStatus.DRAFT,actor,"ASSIGNED",now);
        auditService.success("MEDICAL_HISTORY_QUESTIONNAIRE_ASSIGNED","MEDICAL_HISTORY","MedicalHistoryQuestionnaire",q.getId(),actorId);
        return response(q);
    }

    @Override @Transactional
    public MedicalHistoryQuestionnaireResponse createChangeProposal(CreateMedicalHistoryChangeProposalRequest request,UUID userId){
        if(request==null || (request.source()!=QuestionnaireSource.APP && request.source()!=QuestionnaireSource.WEB))throw new BadRequestException("Patient change proposal source must be APP or WEB");
        Patient patient=patientForUser(userId);User actor=actor(userId);MedicalHistoryVersion current=versionRepository.findByPatient_IdAndCurrentTrue(patient.getId()).orElseThrow(()->new ConflictException("A validated medical history is required before proposing changes"));
        Instant now=clock.instant();var q=new MedicalHistoryQuestionnaire(UUID.randomUUID(),patient,currentTemplate(),QuestionnairePurpose.CHANGE_PROPOSAL,request.source(),current,null,null,now);
        q=questionnaireRepository.saveAndFlush(q);transition(q,null,QuestionnaireStatus.DRAFT,actor,"CHANGE_PROPOSED",now);
        return response(q,true);
    }

    @Override @Transactional(readOnly=true)
    public Page<MedicalHistoryQuestionnaireSummaryResponse> search(UUID patientId,QuestionnaireStatus status,QuestionnaireSource source,Instant from,Instant to,int page,int size){validatePage(page,size);if(from!=null&&to!=null&&from.isAfter(to))throw new BadRequestException("From date must not be after to date");return questionnaireRepository.findAll(MedicalHistoryQuestionnaireSpecifications.matching(patientId,status,source,from,to),PageRequest.of(page,Math.min(size,MAX_PAGE_SIZE),QUESTIONNAIRE_ORDER)).map(this::summary);}
    @Override @Transactional(readOnly=true) public Page<MedicalHistoryQuestionnaireSummaryResponse> listMine(UUID userId,int page,int size){validatePage(page,size);patientForUser(userId);return questionnaireRepository.findByPatient_User_Id(userId,PageRequest.of(page,Math.min(size,MAX_PAGE_SIZE),QUESTIONNAIRE_ORDER)).map(this::summary);}
    @Override @Transactional public MedicalHistoryQuestionnaireResponse getForStaff(UUID patientId,UUID questionnaireId){var q=detailed(questionnaireId,false);requirePatient(q,patientId);auditService.success("MEDICAL_HISTORY_QUESTIONNAIRE_READ","MEDICAL_HISTORY","MedicalHistoryQuestionnaire",q.getId(),null);return response(q);}
    @Override @Transactional(readOnly=true) public MedicalHistoryQuestionnaireResponse getMine(UUID userId,UUID questionnaireId){var q=detailed(questionnaireId,false);requireOwner(q,userId);return response(q,true);}

    @Override @Transactional public MedicalHistoryQuestionnaireSummaryResponse markDelivered(UUID patientId,UUID questionnaireId,UUID actorId){var q=locked(questionnaireId);requirePatient(q,patientId);if(q.getDeliveredAt()!=null)return summary(q);User actor=actor(actorId);q.delivered(actor,clock.instant());questionnaireRepository.saveAndFlush(q);auditService.success("MEDICAL_HISTORY_QUESTIONNAIRE_DELIVERED","MEDICAL_HISTORY","MedicalHistoryQuestionnaire",q.getId(),actorId);return summary(q);}
    @Override @Transactional public MedicalHistoryQuestionnaireSummaryResponse markReceived(UUID patientId,UUID questionnaireId,ReceiveMedicalHistoryQuestionnaireRequest request,UUID actorId){var q=locked(questionnaireId);requirePatient(q,patientId);if(q.getReceivedAt()!=null)return summary(q);if(q.getSource()!=QuestionnaireSource.PAPER&&q.getSource()!=QuestionnaireSource.TRANSCRIPTION)throw new ConflictException("Only paper questionnaires can be received manually");ClinicalDocument scan=document(patientId,request==null?null:request.scanDocumentId(),false);User actor=actor(actorId);q.received(actor,scan,clock.instant());questionnaireRepository.saveAndFlush(q);auditService.success("MEDICAL_HISTORY_QUESTIONNAIRE_RECEIVED","MEDICAL_HISTORY","MedicalHistoryQuestionnaire",q.getId(),actorId);return summary(q);}

    @Override @Transactional public MedicalHistoryQuestionnaireResponse saveAnswersForStaff(UUID patientId,UUID questionnaireId,SaveMedicalHistoryAnswersRequest request,UUID actorId){var q=locked(questionnaireId);requirePatient(q,patientId);return saveAnswers(q,request,actor(actorId));}
    @Override @Transactional public MedicalHistoryQuestionnaireResponse saveMyAnswers(UUID userId,UUID questionnaireId,SaveMedicalHistoryAnswersRequest request){var q=locked(questionnaireId);requireOwner(q,userId);requireDigitalSource(q);saveAnswers(q,request,actor(userId));return response(q,true);}

    @Override @Transactional public MedicalHistoryQuestionnaireResponse submitForStaff(UUID patientId,UUID questionnaireId,SubmitMedicalHistoryQuestionnaireRequest request,UUID actorId){var q=locked(questionnaireId);requirePatient(q,patientId);return submit(q,request,actor(actorId),false);}
    @Override @Transactional public MedicalHistoryQuestionnaireResponse submitMine(UUID userId,UUID questionnaireId,SubmitMedicalHistoryQuestionnaireRequest request){var q=locked(questionnaireId);requireOwner(q,userId);requireDigitalSource(q);if(request.attestationType()!=MedicalHistoryAttestationType.PATIENT_ELECTRONIC)throw new BadRequestException("Patient submission requires electronic patient attestation");submit(q,request,actor(userId),true);return response(q,true);}

    @Override @Transactional public MedicalHistoryQuestionnaireResponse startReview(UUID patientId,UUID questionnaireId,MedicalHistoryVersionedActionRequest request,UUID actorId){var q=locked(questionnaireId);requirePatient(q,patientId);if(q.getStatus()==QuestionnaireStatus.UNDER_REVIEW)return response(q);assertVersion(q,request.lockVersion());requireStatus(q,QuestionnaireStatus.SUBMITTED);User actor=actor(actorId);var from=q.getStatus();Instant now=clock.instant();q.startReview(actor,now);questionnaireRepository.saveAndFlush(q);transition(q,from,q.getStatus(),actor,"REVIEW_STARTED",now);return response(q);}
    @Override @Transactional public MedicalHistoryQuestionnaireResponse addReviewNote(UUID patientId,UUID questionnaireId,MedicalHistoryReviewNoteRequest request,UUID actorId){var q=locked(questionnaireId);requirePatient(q,patientId);if(q.getStatus()!=QuestionnaireStatus.UNDER_REVIEW&&q.getStatus()!=QuestionnaireStatus.CLARIFICATION_REQUIRED)throw new ConflictException("Review notes require a questionnaire under review or clarification");noteRepository.save(new MedicalHistoryReviewNote(UUID.randomUUID(),q,actor(actorId),MedicalHistoryReviewNoteType.INTERNAL,text(request.note(),2000),clock.instant()));return response(q);}
    @Override @Transactional public MedicalHistoryQuestionnaireResponse requestClarification(UUID patientId,UUID questionnaireId,MedicalHistoryTransitionRequest request,UUID actorId){var q=locked(questionnaireId);requirePatient(q,patientId);assertVersion(q,request.lockVersion());requireStatus(q,QuestionnaireStatus.UNDER_REVIEW);User actor=actor(actorId);String reason=text(request.reason(),1000);Instant now=clock.instant();var from=q.getStatus();noteRepository.save(new MedicalHistoryReviewNote(UUID.randomUUID(),q,actor,MedicalHistoryReviewNoteType.CLARIFICATION_REQUEST,reason,now));q.clarify(reason,now);questionnaireRepository.saveAndFlush(q);transition(q,from,q.getStatus(),actor,"CLARIFICATION_REQUESTED",now);return response(q);}

    @Override @Transactional
    public MedicalHistoryQuestionnaireResponse validate(UUID patientId,UUID questionnaireId,MedicalHistoryVersionedActionRequest request,UUID actorId){
        var q=locked(questionnaireId);requirePatient(q,patientId);if(q.getStatus()==QuestionnaireStatus.VALIDATED)return response(q);assertVersion(q,request.lockVersion());requireStatus(q,QuestionnaireStatus.UNDER_REVIEW);User dentist=actor(actorId);Patient patient=patientRepository.findByIdForUpdate(patientId).orElseThrow(()->new ResourceNotFoundException("Patient not found"));var revision=latestRevision(q.getId());Instant now=clock.instant();MedicalHistoryVersion previous=versionRepository.findCurrentForUpdate(patientId).orElse(null);if(previous!=null)previous.supersede();String snapshot=validatedSnapshot(q,revision);int number=(int)versionRepository.countByPatient_Id(patientId)+1;var version=new MedicalHistoryVersion(UUID.randomUUID(),patient,number,MedicalHistoryVersionSource.QUESTIONNAIRE,q,revision.getRevisionNumber(),previous,dentist,now,snapshot,true);version=versionRepository.saveAndFlush(version);attestationRepository.save(new MedicalHistoryAttestation(UUID.randomUUID(),q,revision.getRevisionNumber(),MedicalHistoryAttestationType.PROFESSIONAL_ATTESTATION,dentist,dentist.getFullName(),null,now));syncLegacyProjection(patient,snapshot,now,version);var from=q.getStatus();q.validate(dentist,now);questionnaireRepository.saveAndFlush(q);transition(q,from,q.getStatus(),dentist,"CLINICALLY_VALIDATED",now);auditService.success("MEDICAL_HISTORY_VALIDATED","MEDICAL_HISTORY","MedicalHistoryQuestionnaire",q.getId(),actorId);return response(q);
    }

    @Override @Transactional public MedicalHistoryQuestionnaireResponse reject(UUID patientId,UUID questionnaireId,MedicalHistoryTransitionRequest request,UUID actorId){var q=locked(questionnaireId);requirePatient(q,patientId);if(q.getStatus()==QuestionnaireStatus.REJECTED)return response(q);assertVersion(q,request.lockVersion());requireStatus(q,QuestionnaireStatus.UNDER_REVIEW);User actor=actor(actorId);Instant now=clock.instant();var from=q.getStatus();q.reject(actor,text(request.reason(),1000),now);questionnaireRepository.saveAndFlush(q);transition(q,from,q.getStatus(),actor,"REJECTED",now);return response(q);}
    @Override @Transactional public MedicalHistoryQuestionnaireResponse cancelForStaff(UUID patientId,UUID questionnaireId,MedicalHistoryTransitionRequest request,UUID actorId){var q=locked(questionnaireId);requirePatient(q,patientId);if(q.getStatus()==QuestionnaireStatus.CANCELLED)return response(q);assertVersion(q,request.lockVersion());if(q.getStatus()!=QuestionnaireStatus.DRAFT&&q.getStatus()!=QuestionnaireStatus.CLARIFICATION_REQUIRED)throw new ConflictException("Only a draft or clarification questionnaire can be cancelled");User actor=actor(actorId);Instant now=clock.instant();var from=q.getStatus();q.cancel(actor,text(request.reason(),1000),now);questionnaireRepository.saveAndFlush(q);transition(q,from,q.getStatus(),actor,"STAFF_CANCELLED",now);auditService.success("MEDICAL_HISTORY_QUESTIONNAIRE_CANCELLED","MEDICAL_HISTORY","MedicalHistoryQuestionnaire",q.getId(),actorId);return response(q);}
    @Override @Transactional public MedicalHistoryQuestionnaireResponse cancelMine(UUID userId,UUID questionnaireId,MedicalHistoryTransitionRequest request){var q=locked(questionnaireId);requireOwner(q,userId);if(q.getStatus()==QuestionnaireStatus.CANCELLED)return response(q,true);assertVersion(q,request.lockVersion());if(q.getStatus()!=QuestionnaireStatus.DRAFT&&q.getStatus()!=QuestionnaireStatus.CLARIFICATION_REQUIRED)throw new ConflictException("Only a draft or clarification questionnaire can be cancelled");User actor=actor(userId);Instant now=clock.instant();var from=q.getStatus();q.cancel(actor,text(request.reason(),1000),now);questionnaireRepository.saveAndFlush(q);transition(q,from,q.getStatus(),actor,"PATIENT_CANCELLED",now);return response(q,true);}

    @Override @Transactional(readOnly=true) public Page<MedicalHistoryVersionResponse> listVersions(UUID patientId,int page,int size){validatePage(page,size);if(!patientRepository.existsById(patientId))throw new ResourceNotFoundException("Patient not found");return versionRepository.findByPatient_Id(patientId,PageRequest.of(page,Math.min(size,MAX_PAGE_SIZE),VERSION_ORDER)).map(this::versionResponse);}
    @Override @Transactional public MedicalHistoryVersionResponse getVersion(UUID patientId,UUID versionId){MedicalHistoryVersion version=versionRepository.findByIdAndPatient_Id(requireId(versionId,"Medical history version id is required"),requireId(patientId,"Patient id is required")).orElseThrow(()->new ResourceNotFoundException("Medical history version not found"));auditService.success("MEDICAL_HISTORY_VERSION_READ","MEDICAL_HISTORY","MedicalHistoryVersion",version.getId(),null);return versionResponse(version);}
    @Override @Transactional public MedicalHistoryVersionResponse getMyCurrentVersion(UUID userId){Patient p=patientForUser(userId);MedicalHistoryVersion version=versionRepository.findByPatient_IdAndCurrentTrue(p.getId()).orElseThrow(()->new ResourceNotFoundException("Validated medical history not found"));auditService.success("MEDICAL_HISTORY_VERSION_READ","MEDICAL_HISTORY","MedicalHistoryVersion",version.getId(),userId);return versionResponse(version);}
    @Override @Transactional(readOnly=true) public List<MedicalHistoryTransitionEventResponse> audit(UUID patientId,UUID questionnaireId){var q=detailed(questionnaireId,false);requirePatient(q,patientId);return transitionRepository.findByQuestionnaire_IdOrderByOccurredAtAsc(q.getId()).stream().map(e->new MedicalHistoryTransitionEventResponse(e.getFromStatus(),e.getToStatus(),e.getActor().getId(),e.getActor().getFullName(),e.getReasonCode(),e.getOccurredAt())).toList();}

    private MedicalHistoryQuestionnaireResponse saveAnswers(MedicalHistoryQuestionnaire q,SaveMedicalHistoryAnswersRequest request,User actor){
        if(request==null)throw new BadRequestException("Answers are required");assertVersion(q,request.lockVersion());if(q.getStatus()!=QuestionnaireStatus.DRAFT&&q.getStatus()!=QuestionnaireStatus.CLARIFICATION_REQUIRED)throw new ConflictException("Answers can only be edited in draft or clarification status");if(q.getExpiresAt()!=null&&clock.instant().isAfter(q.getExpiresAt()))throw new ConflictException("Questionnaire assignment has expired");Map<UUID,MedicalHistoryTemplateQuestion> questions=q.getTemplateVersion().getSections().stream().flatMap(s->s.getQuestions().stream()).collect(Collectors.toMap(MedicalHistoryTemplateQuestion::getId,Function.identity()));Set<UUID> seen=new HashSet<>();List<MedicalHistoryQuestionnaireAnswer> answers=new ArrayList<>();Instant now=clock.instant();for(var item:request.answers()){if(!seen.add(item.questionId()))throw new BadRequestException("A question cannot be answered more than once");var question=questions.get(item.questionId());if(question==null)throw new BadRequestException("Answer references a question outside this template version");validateAnswer(question,item.value(),item.note());answers.add(new MedicalHistoryQuestionnaireAnswer(UUID.randomUUID(),q,question,json(item.value()),optional(item.note(),2000),now));}q.replaceAnswers(answers,now);try{questionnaireRepository.saveAndFlush(q);}catch(OptimisticLockingFailureException ex){throw new ConflictException("Questionnaire was updated by another request");}auditService.success("MEDICAL_HISTORY_DRAFT_SAVED","MEDICAL_HISTORY","MedicalHistoryQuestionnaire",q.getId(),actor.getId());return response(q);
    }

    private MedicalHistoryQuestionnaireResponse submit(MedicalHistoryQuestionnaire q,SubmitMedicalHistoryQuestionnaireRequest request,User actor,boolean patientSubmission){
        if(request==null)throw new BadRequestException("Submission is required");if(q.getStatus()==QuestionnaireStatus.SUBMITTED)return response(q);assertVersion(q,request.lockVersion());if(q.getStatus()!=QuestionnaireStatus.DRAFT&&q.getStatus()!=QuestionnaireStatus.CLARIFICATION_REQUIRED)throw new ConflictException("Questionnaire cannot be submitted from its current status");if(q.getExpiresAt()!=null&&clock.instant().isAfter(q.getExpiresAt()))throw new ConflictException("Questionnaire assignment has expired");validateRequiredAnswers(q);ClinicalDocument evidence=document(q.getPatient().getId(),request.evidenceDocumentId(),request.attestationType()==MedicalHistoryAttestationType.HANDWRITTEN_SCAN);if(request.attestationType()==MedicalHistoryAttestationType.HANDWRITTEN_SCAN&&evidence==null)throw new BadRequestException("Handwritten attestation requires a scanned document");if(patientSubmission&&request.attestationType()!=MedicalHistoryAttestationType.PATIENT_ELECTRONIC)throw new BadRequestException("Patient submission requires electronic attestation");int number=revisionRepository.findFirstByQuestionnaire_IdOrderByRevisionNumberDesc(q.getId()).map(r->r.getRevisionNumber()+1).orElse(1);Instant now=clock.instant();String snapshot=answerSnapshot(q);revisionRepository.save(new MedicalHistoryAnswerRevision(UUID.randomUUID(),q,number,snapshot,now,actor));attestationRepository.save(new MedicalHistoryAttestation(UUID.randomUUID(),q,number,request.attestationType(),actor,text(request.signerName(),180),evidence,now));var from=q.getStatus();q.submit(actor,now);questionnaireRepository.saveAndFlush(q);transition(q,from,q.getStatus(),actor,"SUBMITTED",now);auditService.success("MEDICAL_HISTORY_QUESTIONNAIRE_SUBMITTED","MEDICAL_HISTORY","MedicalHistoryQuestionnaire",q.getId(),actor.getId());return response(q);
    }

    private void validateTemplateRequest(CreateMedicalHistoryTemplateRequest request){validateSections(request.sections());}
    private void validateSections(List<CreateMedicalHistoryTemplateRequest.Section> sections){Set<String> keys=new HashSet<>();for(var section:sections){for(var q:section.questions()){String key=text(q.key(),100).toUpperCase(Locale.ROOT);if(!keys.add(key))throw new BadRequestException("Question keys must be unique within a template version");if((q.answerType()==MedicalHistoryAnswerType.SINGLE_CHOICE||q.answerType()==MedicalHistoryAnswerType.MULTIPLE_CHOICE)&&(q.choices()==null||q.choices().isEmpty()))throw new BadRequestException("Choice questions require at least one option");if(q.minValue()!=null&&q.maxValue()!=null&&q.minValue().compareTo(q.maxValue())>0)throw new BadRequestException("Question minimum value must not exceed maximum value");}}}
    private List<String> normalizeLegacy(List<String> values,String field){if(values==null||values.size()>100)throw new BadRequestException(field+" must contain between 0 and 100 entries");Map<String,String> unique=new LinkedHashMap<>();for(String value:values){String normalized=text(value,200);unique.putIfAbsent(normalized.toLowerCase(Locale.ROOT),normalized);}return List.copyOf(unique.values());}
    private void validateAnswer(MedicalHistoryTemplateQuestion q,JsonNode value,String note){if(value==null||value.isNull())throw new BadRequestException("Answer value is required");switch(q.getAnswerType()){case YES_NO_UNKNOWN_NA->{if(!value.isTextual()||!BOOLEAN_VALUES.contains(value.asText()))throw new BadRequestException("Invalid yes/no/unknown/not-applicable answer");}case SINGLE_CHOICE->{if(!value.isTextual()||!choices(q).contains(value.asText()))throw new BadRequestException("Invalid choice answer");}case MULTIPLE_CHOICE->{if(!value.isArray()||!allChoicesAllowed(value,choices(q)))throw new BadRequestException("Invalid multiple-choice answer");}case SHORT_TEXT,LONG_TEXT->{if(!value.isTextual())throw new BadRequestException("Text answer is required");int max=q.getMaxLength()!=null?q.getMaxLength():(q.getAnswerType()==MedicalHistoryAnswerType.SHORT_TEXT?500:4000);if(value.asText().length()>max)throw new BadRequestException("Answer exceeds the allowed length");}case DATE->{if(!value.isTextual())throw new BadRequestException("ISO date answer is required");try{LocalDate.parse(value.asText());}catch(DateTimeException e){throw new BadRequestException("Invalid ISO date answer");}}case NUMBER->{if(!value.isNumber())throw new BadRequestException("Numeric answer is required");BigDecimal n=value.decimalValue();if(q.getMinValue()!=null&&n.compareTo(q.getMinValue())<0||q.getMaxValue()!=null&&n.compareTo(q.getMaxValue())>0)throw new BadRequestException("Numeric answer is outside the allowed range");}}if(note!=null&&!note.isBlank()&&!q.isNotesAllowed())throw new BadRequestException("Notes are not allowed for this question");if(q.getConditionalNoteJson()!=null){JsonNode rule=read(q.getConditionalNoteJson());if(Objects.equals(rule.path("equals").asText(),value.asText())&&(note==null||note.isBlank()))throw new BadRequestException("A note is required for this answer");}}
    private void validateRequiredAnswers(MedicalHistoryQuestionnaire q){Set<UUID> answered=q.getAnswers().stream().map(a->a.getQuestion().getId()).collect(Collectors.toSet());for(var section:q.getTemplateVersion().getSections())for(var question:section.getQuestions())if(question.isRequired()&&!answered.contains(question.getId()))throw new BadRequestException("All required questions must be answered");}

    private String answerSnapshot(MedicalHistoryQuestionnaire q){List<Map<String,Object>> answers=q.getAnswers().stream().sorted(Comparator.comparing(a->a.getQuestion().getQuestionKey())).map(a->{Map<String,Object> m=new LinkedHashMap<>();m.put("questionId",a.getQuestion().getId());m.put("questionKey",a.getQuestion().getQuestionKey());m.put("value",read(a.getAnswerValue()));m.put("note",a.getNote());return m;}).toList();return json(Map.of("templateVersionId",q.getTemplateVersion().getId(),"answers",answers));}
    private String validatedSnapshot(MedicalHistoryQuestionnaire q,MedicalHistoryAnswerRevision revision){JsonNode answers=read(revision.getAnswersSnapshot());Map<String,Object> summary=deriveSummary(answers);Map<String,Object> snapshot=new LinkedHashMap<>();snapshot.put("templateVersionId",q.getTemplateVersion().getId());snapshot.put("questionnaireId",q.getId());snapshot.put("revisionNumber",revision.getRevisionNumber());snapshot.put("answers",answers.path("answers"));snapshot.putAll(summary);return json(snapshot);}
    private Map<String,Object> deriveSummary(JsonNode root){Map<String,Object> result=new LinkedHashMap<>();result.put("allergies",List.of());result.put("currentMedications",List.of());result.put("relevantConditions",List.of());result.put("observations",null);for(JsonNode answer:root.path("answers")){String key=answer.path("questionKey").asText();JsonNode value=answer.path("value");switch(key){case "ALLERGIES"->result.put("allergies",strings(value));case "CURRENT_MEDICATIONS"->result.put("currentMedications",strings(value));case "RELEVANT_CONDITIONS"->result.put("relevantConditions",strings(value));case "OBSERVATIONS"->result.put("observations",value.isTextual()?value.asText():null);default->{}}}return result;}
    private void syncLegacyProjection(Patient patient,String snapshot,Instant now,MedicalHistoryVersion currentVersion){JsonNode node=read(snapshot);MedicalHistory history=medicalHistoryRepository.findByPatient_Id(patient.getId()).orElseGet(()->new MedicalHistory(UUID.randomUUID(),patient,now,now));history.replaceAllergies(strings(node.path("allergies")));history.replaceCurrentMedications(strings(node.path("currentMedications")));history.replaceRelevantConditions(strings(node.path("relevantConditions")));history.setObservations(node.path("observations").isTextual()?node.path("observations").asText():null);history.setCurrentVersion(currentVersion);history.setUpdatedAt(now);medicalHistoryRepository.saveAndFlush(history);}

    private MedicalHistoryTemplateResponse templateResponse(MedicalHistoryTemplateVersion v){return new MedicalHistoryTemplateResponse(v.getTemplate().getId(),v.getTemplate().getCode(),v.getTemplate().getName(),v.getId(),v.getVersionNumber(),v.getTitle(),v.getStatus(),v.getPublishedAt(),v.getSections().stream().map(s->new MedicalHistoryTemplateResponse.Section(s.getId(),s.getSectionKey(),s.getTitle(),s.getDescription(),s.getDisplayOrder(),s.getQuestions().stream().map(q->new MedicalHistoryTemplateResponse.Question(q.getId(),q.getQuestionKey(),q.getPrompt(),q.getAnswerType(),q.getDisplayOrder(),q.isRequired(),q.isNotesAllowed(),q.getMaxLength(),q.getMinValue(),q.getMaxValue(),readNullable(q.getChoicesJson()),readNullable(q.getConditionalNoteJson()))).toList())).toList());}
    private MedicalHistoryQuestionnaireSummaryResponse summary(MedicalHistoryQuestionnaire q){return new MedicalHistoryQuestionnaireSummaryResponse(q.getId(),q.getPatient().getId(),q.getPatient().getName(),q.getPurpose(),q.getSource(),q.getStatus(),q.getLockVersion(),q.getTemplateVersion().getVersionNumber(),q.getTemplateVersion().getTitle(),q.getExpiresAt(),q.getSubmittedAt(),q.getValidatedAt(),q.getStatusReason(),q.getUpdatedAt());}
    private MedicalHistoryQuestionnaireResponse response(MedicalHistoryQuestionnaire q){return response(q,false);}
    private MedicalHistoryQuestionnaireResponse response(MedicalHistoryQuestionnaire q,boolean patientView){List<MedicalHistoryQuestionnaireResponse.Answer> answers=q.getAnswers().stream().map(a->new MedicalHistoryQuestionnaireResponse.Answer(a.getQuestion().getId(),a.getQuestion().getQuestionKey(),a.getQuestion().getPrompt(),a.getQuestion().getAnswerType(),read(a.getAnswerValue()),a.getNote())).toList();List<MedicalHistoryQuestionnaireResponse.ReviewNote> notes=noteRepository.findByQuestionnaire_IdOrderByCreatedAtAsc(q.getId()).stream().filter(n->!patientView||n.getNoteType()!=MedicalHistoryReviewNoteType.INTERNAL).map(n->new MedicalHistoryQuestionnaireResponse.ReviewNote(n.getId(),n.getNoteType(),n.getNoteText(),n.getAuthor().getId(),n.getAuthor().getFullName(),n.getCreatedAt())).toList();List<MedicalHistoryQuestionnaireResponse.Attestation> attestations=attestationRepository.findByQuestionnaire_IdOrderByAttestedAtAsc(q.getId()).stream().map(a->new MedicalHistoryQuestionnaireResponse.Attestation(a.getType(),a.getRevisionNumber(),a.getSignerName(),a.getAttestedAt(),a.getEvidenceDocument()==null?null:a.getEvidenceDocument().getId())).toList();return new MedicalHistoryQuestionnaireResponse(q.getId(),q.getPatient().getId(),q.getPatient().getName(),q.getPurpose(),q.getSource(),q.getStatus(),q.getLockVersion(),q.getTemplateVersion().getId(),q.getTemplateVersion().getVersionNumber(),q.getTemplateVersion().getTitle(),templateResponse(q.getTemplateVersion()),q.getExpiresAt(),q.getDeliveredAt(),q.getReceivedAt(),q.getSubmittedAt(),q.getReviewStartedAt(),q.getValidatedAt(),q.getStatusReason(),q.getScanDocument()==null?null:q.getScanDocument().getId(),q.getCreatedAt(),q.getUpdatedAt(),answers,notes,attestations);}
    private MedicalHistoryVersionResponse versionResponse(MedicalHistoryVersion v){return new MedicalHistoryVersionResponse(v.getId(),v.getPatient().getId(),v.getVersionNumber(),v.getSource(),v.getSourceQuestionnaire()==null?null:v.getSourceQuestionnaire().getId(),v.getSourceRevisionNumber(),v.getValidatedBy()==null?null:v.getValidatedBy().getId(),v.getValidatedBy()==null?null:v.getValidatedBy().getFullName(),v.getValidatedAt(),read(v.getSnapshot()),v.isCurrent());}

    private MedicalHistoryTemplateVersion currentTemplate(){return templateVersionRepository.findFirstByStatusOrderByPublishedAtDesc(MedicalHistoryTemplateStatus.PUBLISHED).orElseThrow(()->new ConflictException("No published medical history template is available"));}
    private MedicalHistoryQuestionnaire detailed(UUID id,boolean lock){return (lock?questionnaireRepository.findDetailedByIdForUpdate(requireId(id,"Questionnaire id is required")):questionnaireRepository.findDetailedById(requireId(id,"Questionnaire id is required"))).orElseThrow(()->new ResourceNotFoundException("Medical history questionnaire not found"));}
    private MedicalHistoryQuestionnaire locked(UUID id){return detailed(id,true);} private MedicalHistoryAnswerRevision latestRevision(UUID id){return revisionRepository.findFirstByQuestionnaire_IdOrderByRevisionNumberDesc(id).orElseThrow(()->new ConflictException("Submitted answer snapshot not found"));}
    private Patient patient(UUID id){return patientRepository.findById(requireId(id,"Patient id is required")).orElseThrow(()->new ResourceNotFoundException("Patient not found"));} private Patient patientForUser(UUID userId){return patientRepository.findByUser_Id(requireId(userId,"Authenticated user id is required")).orElseThrow(()->new ResourceNotFoundException("Patient not found"));} private User actor(UUID id){return userRepository.findById(requireId(id,"Actor id is required")).orElseThrow(()->new ResourceNotFoundException("User not found"));}
    private ClinicalDocument document(UUID patientId,UUID id,boolean required){if(id==null){if(required)throw new BadRequestException("Scanned document evidence is required");return null;}ClinicalDocument d=documentRepository.findByIdAndPatient_Id(id,patientId).orElseThrow(()->new ResourceNotFoundException("Clinical document not found"));if(!d.hasFile())throw new ConflictException("Clinical document does not contain a stored file");return d;}
    private void requirePatient(MedicalHistoryQuestionnaire q,UUID patientId){if(!q.getPatient().getId().equals(requireId(patientId,"Patient id is required")))throw new ResourceNotFoundException("Medical history questionnaire not found");} private void requireOwner(MedicalHistoryQuestionnaire q,UUID userId){if(q.getPatient().getUser()==null||!q.getPatient().getUser().getId().equals(requireId(userId,"Authenticated user id is required")))throw new ResourceNotFoundException("Medical history questionnaire not found");}
    private void requireStatus(MedicalHistoryQuestionnaire q,QuestionnaireStatus status){if(q.getStatus()!=status)throw new ConflictException("Questionnaire is not in the required status");} private void assertVersion(MedicalHistoryQuestionnaire q,long expected){if(q.getLockVersion()!=expected)throw new ConflictException("Questionnaire was updated by another request");}
    private void requireDigitalSource(MedicalHistoryQuestionnaire q){if(q.getSource()!=QuestionnaireSource.APP&&q.getSource()!=QuestionnaireSource.WEB)throw new ConflictException("Paper questionnaires can only be transcribed by authorized clinical staff");}
    private void transition(MedicalHistoryQuestionnaire q,QuestionnaireStatus from,QuestionnaireStatus to,User actor,String reason,Instant at){transitionRepository.save(new MedicalHistoryTransitionEvent(UUID.randomUUID(),q,from,to,actor,reason,at));}
    private void validateExpiry(Instant expiry,Instant now){if(expiry!=null&&!expiry.isAfter(now))throw new BadRequestException("Questionnaire expiration must be in the future");} private void validatePage(int page,int size){if(page<0||size<1||size>MAX_PAGE_SIZE)throw new BadRequestException("Invalid pagination parameters");}
    private UUID requireId(UUID id,String message){if(id==null)throw new BadRequestException(message);return id;} private String text(String value,int max){if(value==null||value.isBlank())throw new BadRequestException("Required text must not be blank");String s=value.trim();if(s.length()>max)throw new BadRequestException("Text exceeds the allowed length");return s;} private String optional(String value,int max){if(value==null||value.isBlank())return null;String s=value.trim();if(s.length()>max)throw new BadRequestException("Text exceeds the allowed length");return s;}
    private Set<String> choices(MedicalHistoryTemplateQuestion q){JsonNode n=readNullable(q.getChoicesJson());if(n==null||!n.isArray())return Set.of();Set<String> values=new LinkedHashSet<>();n.forEach(v->values.add(v.asText()));return values;} private boolean allChoicesAllowed(JsonNode value,Set<String> allowed){for(JsonNode v:value)if(!v.isTextual()||!allowed.contains(v.asText()))return false;return true;}
    private List<String> strings(JsonNode node){if(node==null||node.isNull())return List.of();if(node.isArray()){List<String> result=new ArrayList<>();node.forEach(v->{if(v.isTextual()&&!v.asText().isBlank())result.add(v.asText().trim());});return result;}if(node.isTextual()&&!node.asText().isBlank())return List.of(node.asText().trim());return List.of();}
    private String json(Object value){try{return objectMapper.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalStateException("Could not serialize medical history data",e);}} private JsonNode read(String value){try{return objectMapper.readTree(value);}catch(JsonProcessingException e){throw new IllegalStateException("Stored medical history data is invalid",e);}} private JsonNode readNullable(String value){return value==null?null:read(value);}
}
