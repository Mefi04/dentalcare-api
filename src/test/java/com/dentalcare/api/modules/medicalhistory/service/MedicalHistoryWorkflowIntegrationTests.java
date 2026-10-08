package com.dentalcare.api.modules.medicalhistory.service;

import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.clinicalrecords.model.*;
import com.dentalcare.api.modules.clinicalrecords.repository.ClinicalDocumentRepository;
import com.dentalcare.api.modules.medicalhistory.dto.request.*;
import com.dentalcare.api.modules.medicalhistory.model.*;
import com.dentalcare.api.modules.medicalhistory.repository.*;
import com.dentalcare.api.modules.patients.model.*;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.users.model.*;
import com.dentalcare.api.modules.users.repository.UserRepository;
import com.dentalcare.api.security.jwt.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest @Testcontainers(disabledWithoutDocker=true) @Transactional
class MedicalHistoryWorkflowIntegrationTests {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);r.add("INITIAL_ADMIN_ENABLED",()->"false");r.add("FRONTEND_URL",()->"http://localhost:3000");}
    @Autowired MedicalHistoryWorkflowService service;
    @Autowired UserRepository userRepository;
    @Autowired PatientRepository patientRepository;
    @Autowired MedicalHistoryVersionRepository versionRepository;
    @Autowired MedicalHistoryRepository historyRepository;
    @Autowired ClinicalDocumentRepository documentRepository;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean JwtService jwtService;
    private User dentist; private User patientUser; private Patient patient;

    @BeforeEach void setUp(){dentist=user("dentist144","8000000000144","Odontóloga Prueba");patientUser=user("patient144","8000000000145","Paciente Prueba");patient=patient(patientUser,"PAC-144","7000000000144");publishTemplate();}

    @Test void validatesImmutableVersionAndAcceptedChangeCreatesNextVersion(){
        var assigned=service.assign(patient.getId(),new AssignMedicalHistoryQuestionnaireRequest(QuestionnaireSource.WEB,Instant.now().plus(Duration.ofDays(5)),null),dentist.getId());
        var submitted=completeAndSubmit(assigned,patientUser);
        var reviewing=service.startReview(patient.getId(),assigned.id(),new MedicalHistoryVersionedActionRequest(submitted.lockVersion()),dentist.getId());
        var validated=service.validate(patient.getId(),assigned.id(),new MedicalHistoryVersionedActionRequest(reviewing.lockVersion()),dentist.getId());
        assertThat(validated.status()).isEqualTo(QuestionnaireStatus.VALIDATED);
        var first=service.getMyCurrentVersion(patientUser.getId());
        assertThat(first.versionNumber()).isEqualTo(1);assertThat(first.snapshot().path("allergies").get(0).asText()).isEqualTo("Penicilina");
        assertThat(historyRepository.findByPatient_Id(patient.getId()).orElseThrow().getAllergies()).containsExactly("Penicilina");

        var rejected=service.createChangeProposal(new CreateMedicalHistoryChangeProposalRequest(QuestionnaireSource.APP),patientUser.getId());
        var rejectedSubmitted=completeAndSubmit(rejected,patientUser);var rejectedReview=service.startReview(patient.getId(),rejected.id(),new MedicalHistoryVersionedActionRequest(rejectedSubmitted.lockVersion()),dentist.getId());
        service.reject(patient.getId(),rejected.id(),new MedicalHistoryTransitionRequest(rejectedReview.lockVersion(),"No corresponde al expediente"),dentist.getId());
        assertThat(service.getMyCurrentVersion(patientUser.getId()).id()).isEqualTo(first.id());

        var accepted=service.createChangeProposal(new CreateMedicalHistoryChangeProposalRequest(QuestionnaireSource.WEB),patientUser.getId());
        var saved=saveAnswers(accepted,List.of("Látex"));var acceptedSubmitted=service.submitMine(patientUser.getId(),accepted.id(),new SubmitMedicalHistoryQuestionnaireRequest(saved.lockVersion(),MedicalHistoryAttestationType.PATIENT_ELECTRONIC,"Paciente Prueba",null));
        var acceptedReview=service.startReview(patient.getId(),accepted.id(),new MedicalHistoryVersionedActionRequest(acceptedSubmitted.lockVersion()),dentist.getId());
        service.validate(patient.getId(),accepted.id(),new MedicalHistoryVersionedActionRequest(acceptedReview.lockVersion()),dentist.getId());
        var second=service.getMyCurrentVersion(patientUser.getId());
        assertThat(second.versionNumber()).isEqualTo(2);assertThat(second.id()).isNotEqualTo(first.id());assertThat(versionRepository.findById(first.id()).orElseThrow().isCurrent()).isFalse();
        var history=historyRepository.findByPatient_Id(patient.getId()).orElseThrow();
        assertThat(history.getAllergies()).containsExactly("Látex");assertThat(history.getCurrentVersion().getId()).isEqualTo(second.id());
    }

    @Test void draftTemplateCanBeEditedButPublishedVersionIsImmutable(){
        var published=service.getCurrentTemplate();
        var replacement=new CreateMedicalHistoryTemplateVersionRequest("Nueva versión",List.of(new CreateMedicalHistoryTemplateRequest.Section("GENERAL","General",null,List.of(question("ALLERGIES",MedicalHistoryAnswerType.MULTIPLE_CHOICE,List.of("Látex"))))));
        assertThatThrownBy(()->service.updateTemplateVersion(published.versionId(),replacement,dentist.getId())).isInstanceOf(com.dentalcare.api.exception.ConflictException.class);
        var draft=service.createTemplateVersion(published.templateId(),replacement,dentist.getId());
        var updated=service.updateTemplateVersion(draft.versionId(),new CreateMedicalHistoryTemplateVersionRequest("Versión corregida",replacement.sections()),dentist.getId());
        assertThat(updated.title()).isEqualTo("Versión corregida");assertThat(updated.sections()).singleElement().satisfies(section->assertThat(section.questions()).hasSize(1));
    }

    @Test void patientOwnershipPreventsIdorAndDuplicateTransitionsAreIdempotent(){
        User other=user("other144","8000000000146","Otro Paciente");patient(other,"PAC-145","7000000000145");
        var assigned=service.assign(patient.getId(),new AssignMedicalHistoryQuestionnaireRequest(QuestionnaireSource.APP,null,null),dentist.getId());
        assertThatThrownBy(()->service.getMine(other.getId(),assigned.id())).isInstanceOf(ResourceNotFoundException.class);
        var submitted=completeAndSubmit(assigned,patientUser);var duplicate=service.submitMine(patientUser.getId(),assigned.id(),new SubmitMedicalHistoryQuestionnaireRequest(submitted.lockVersion(),MedicalHistoryAttestationType.PATIENT_ELECTRONIC,"Paciente Prueba",null));
        assertThat(duplicate.status()).isEqualTo(QuestionnaireStatus.SUBMITTED);
        var review=service.startReview(patient.getId(),assigned.id(),new MedicalHistoryVersionedActionRequest(submitted.lockVersion()),dentist.getId());
        var validated=service.validate(patient.getId(),assigned.id(),new MedicalHistoryVersionedActionRequest(review.lockVersion()),dentist.getId());
        service.validate(patient.getId(),assigned.id(),new MedicalHistoryVersionedActionRequest(validated.lockVersion()),dentist.getId());
        assertThat(versionRepository.countByPatient_Id(patient.getId())).isEqualTo(1);
    }

    @Test void paperSubmissionRequiresAndPreservesStoredScanEvidence(){
        Instant now=Instant.now();ClinicalDocument scan=new ClinicalDocument(UUID.randomUUID(),patient,dentist,"Antecedentes firmados",ClinicalDocumentType.CLINICAL_REPORT,"Formulario papel",LocalDate.now(),now,now);scan.setStorageObjectKey("patients/"+patient.getId()+"/history.pdf");scan.setFileName("antecedentes.pdf");scan.setFileSize(100L);scan.setContentType("application/pdf");scan=documentRepository.saveAndFlush(scan);
        UUID scanId=scan.getId();
        var assigned=service.assign(patient.getId(),new AssignMedicalHistoryQuestionnaireRequest(QuestionnaireSource.PAPER,null,scanId),dentist.getId());
        service.markDelivered(patient.getId(),assigned.id(),dentist.getId());service.markReceived(patient.getId(),assigned.id(),new ReceiveMedicalHistoryQuestionnaireRequest(scan.getId()),dentist.getId());
        var detail=service.getForStaff(patient.getId(),assigned.id());var saved=service.saveAnswersForStaff(patient.getId(),assigned.id(),answerRequest(detail,List.of("Penicilina")),dentist.getId());
        var submitted=service.submitForStaff(patient.getId(),assigned.id(),new SubmitMedicalHistoryQuestionnaireRequest(saved.lockVersion(),MedicalHistoryAttestationType.HANDWRITTEN_SCAN,"Paciente Prueba",scan.getId()),dentist.getId());
        assertThat(submitted.scanDocumentId()).isEqualTo(scanId);assertThat(submitted.attestations()).singleElement().satisfies(a->{assertThat(a.type()).isEqualTo(MedicalHistoryAttestationType.HANDWRITTEN_SCAN);assertThat(a.evidenceDocumentId()).isEqualTo(scanId);});
    }

    private com.dentalcare.api.modules.medicalhistory.dto.response.MedicalHistoryQuestionnaireResponse completeAndSubmit(com.dentalcare.api.modules.medicalhistory.dto.response.MedicalHistoryQuestionnaireResponse q,User owner){var saved=saveAnswers(q,List.of("Penicilina"));return service.submitMine(owner.getId(),q.id(),new SubmitMedicalHistoryQuestionnaireRequest(saved.lockVersion(),MedicalHistoryAttestationType.PATIENT_ELECTRONIC,owner.getFullName(),null));}
    private com.dentalcare.api.modules.medicalhistory.dto.response.MedicalHistoryQuestionnaireResponse saveAnswers(com.dentalcare.api.modules.medicalhistory.dto.response.MedicalHistoryQuestionnaireResponse q,List<String> allergies){return service.saveMyAnswers(patientUser.getId(),q.id(),answerRequest(q,allergies));}
    private SaveMedicalHistoryAnswersRequest answerRequest(com.dentalcare.api.modules.medicalhistory.dto.response.MedicalHistoryQuestionnaireResponse q,List<String> allergies){Map<String,UUID> ids=q.template().sections().stream().flatMap(s->s.questions().stream()).collect(java.util.stream.Collectors.toMap(com.dentalcare.api.modules.medicalhistory.dto.response.MedicalHistoryTemplateResponse.Question::key,com.dentalcare.api.modules.medicalhistory.dto.response.MedicalHistoryTemplateResponse.Question::id));List<SaveMedicalHistoryAnswersRequest.Answer> answers=List.of(new SaveMedicalHistoryAnswersRequest.Answer(ids.get("ALLERGIES"),objectMapper.valueToTree(allergies),null),new SaveMedicalHistoryAnswersRequest.Answer(ids.get("CURRENT_MEDICATIONS"),objectMapper.valueToTree(List.of("Metformina")),null),new SaveMedicalHistoryAnswersRequest.Answer(ids.get("RELEVANT_CONDITIONS"),objectMapper.valueToTree(List.of("Diabetes")),null),new SaveMedicalHistoryAnswersRequest.Answer(ids.get("OBSERVATIONS"),objectMapper.valueToTree("Controlada"),null));return new SaveMedicalHistoryAnswersRequest(q.lockVersion(),answers);}
    private void publishTemplate(){var questions=List.of(question("ALLERGIES",MedicalHistoryAnswerType.MULTIPLE_CHOICE,List.of("Penicilina","Látex")),question("CURRENT_MEDICATIONS",MedicalHistoryAnswerType.MULTIPLE_CHOICE,List.of("Metformina")),question("RELEVANT_CONDITIONS",MedicalHistoryAnswerType.MULTIPLE_CHOICE,List.of("Diabetes")),new CreateMedicalHistoryTemplateRequest.Question("OBSERVATIONS","Observaciones",MedicalHistoryAnswerType.LONG_TEXT,true,false,1000,null,null,null,null));var request=new CreateMedicalHistoryTemplateRequest("GENERAL_144","Antecedentes generales","Cuestionario clínico",List.of(new CreateMedicalHistoryTemplateRequest.Section("GENERAL","Información clínica",null,questions)));var draft=service.createTemplate(request,dentist.getId());service.publishTemplate(draft.versionId(),dentist.getId());}
    private CreateMedicalHistoryTemplateRequest.Question question(String key,MedicalHistoryAnswerType type,List<String> choices){return new CreateMedicalHistoryTemplateRequest.Question(key,key,type,true,false,null,BigDecimal.ZERO,null,choices,null);}
    private User user(String username,String cui,String name){Instant now=Instant.now();return userRepository.saveAndFlush(new User(UUID.randomUUID(),username,name,username+"@example.test",cui,"hash",UserStatus.ACTIVE,now,now));}
    private Patient patient(User user,String code,String dpi){Instant now=Instant.now();Patient p=new Patient();p.setId(UUID.randomUUID());p.setCode(code);p.setName(user.getFullName());p.setDpi(dpi);p.setUser(user);p.setBirthDate(LocalDate.of(1990,1,1));p.setGender(Gender.OTHER);p.setPhone("55550000");p.setCreatedAt(now);p.setUpdatedAt(now);return patientRepository.saveAndFlush(p);}
}
