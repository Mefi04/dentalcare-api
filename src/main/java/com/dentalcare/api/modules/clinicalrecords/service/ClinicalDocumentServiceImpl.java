package com.dentalcare.api.modules.clinicalrecords.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.exception.UnauthorizedException;
import com.dentalcare.api.modules.clinicalrecords.dto.request.CreateClinicalDocumentRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.request.UploadClinicalDocumentRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalDocumentDownload;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalDocumentResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.PatientClinicalDocumentResponse;
import com.dentalcare.api.modules.clinicalrecords.mapper.ClinicalDocumentMapper;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDocument;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDocumentType;
import com.dentalcare.api.modules.clinicalrecords.repository.ClinicalDocumentRepository;
import com.dentalcare.api.modules.clinicalrecords.storage.ClinicalDocumentStorage;
import com.dentalcare.api.modules.clinicalrecords.storage.StoredDocument;
import com.dentalcare.api.modules.clinicalrecords.storage.StoredDocumentContent;
import com.dentalcare.api.modules.clinicalrecords.storage.UploadDocumentCommand;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class ClinicalDocumentServiceImpl implements ClinicalDocumentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClinicalDocumentServiceImpl.class);
    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort DOCUMENT_ORDER = Sort.by(
            Sort.Order.desc("documentDate"),
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id")
    );

    private final ClinicalDocumentRepository clinicalDocumentRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final ClinicalDocumentMapper mapper;
    private final ClinicalDocumentStorage clinicalDocumentStorage;
    private final ClinicalDocumentFileValidator fileValidator;
    private final Clock clock;

    public ClinicalDocumentServiceImpl(ClinicalDocumentRepository clinicalDocumentRepository,
                                       PatientRepository patientRepository,
                                       UserRepository userRepository,
                                       ClinicalDocumentMapper mapper,
                                       ClinicalDocumentStorage clinicalDocumentStorage,
                                       ClinicalDocumentFileValidator fileValidator,
                                       Clock clock) {
        this.clinicalDocumentRepository = clinicalDocumentRepository;
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
        this.mapper = mapper;
        this.clinicalDocumentStorage = clinicalDocumentStorage;
        this.fileValidator = fileValidator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ClinicalDocumentResponse createDocument(UUID patientId,
                                                   CreateClinicalDocumentRequest request,
                                                   UUID authenticatedUserId) {
        requireId(patientId, "Patient id is required");
        requireId(authenticatedUserId, "Authentication is required");

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));

        User author = findActiveUser(authenticatedUserId);

        Instant now = clock.instant();
        LocalDate documentDate = request.documentDate() != null
                ? request.documentDate()
                : LocalDate.ofInstant(now, clock.getZone());

        String description = null;
        if (request.description() != null && !request.description().trim().isEmpty()) {
            description = request.description().trim();
        }

        ClinicalDocument document = new ClinicalDocument(
                UUID.randomUUID(),
                patient,
                author,
                request.title().trim(),
                request.type(),
                description,
                documentDate,
                now,
                now
        );

        ClinicalDocument saved = clinicalDocumentRepository.save(document);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ClinicalDocumentResponse uploadDocument(UUID patientId,
                                                   UploadClinicalDocumentRequest request,
                                                   UUID authenticatedUserId) {
        requireId(patientId, "Patient id is required");
        requireId(authenticatedUserId, "Authentication is required");
        if (request == null) {
            throw new BadRequestException("Upload request is required");
        }

        fileValidator.validate(request.file());

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));

        User author = findActiveUser(authenticatedUserId);

        Instant now = clock.instant();
        LocalDate documentDate = request.documentDate() != null
                ? request.documentDate()
                : LocalDate.ofInstant(now, clock.getZone());

        String description = null;
        if (request.description() != null && !request.description().trim().isEmpty()) {
            description = request.description().trim();
        }

        StoredDocument stored;
        try (InputStream is = request.file().getInputStream()) {
            UploadDocumentCommand uploadCommand = new UploadDocumentCommand(
                    patientId,
                    request.file().getOriginalFilename(),
                    request.file().getContentType(),
                    request.file().getSize(),
                    is
            );
            stored = clinicalDocumentStorage.store(uploadCommand);
        } catch (IOException e) {
            LOGGER.error("Failed to read upload file stream for patient {}", patientId, e);
            throw new BadRequestException("Failed to read uploaded file");
        }

        ClinicalDocument document = new ClinicalDocument(
                UUID.randomUUID(),
                patient,
                author,
                request.title().trim(),
                request.type(),
                description,
                documentDate,
                now,
                now,
                stored.storageObjectKey(),
                stored.fileName(),
                stored.fileSize(),
                stored.contentType()
        );

        ClinicalDocument saved;
        try {
            saved = clinicalDocumentRepository.saveAndFlush(document);
        } catch (Exception e) {
            LOGGER.error("Database persistence failed after storing document in R2. Executing compensating delete for key: {}", stored.storageObjectKey(), e);
            try {
                clinicalDocumentStorage.delete(stored.storageObjectKey());
            } catch (Exception deleteException) {
                LOGGER.error("CRITICAL: Compensating delete failed for orphaned R2 object with key: {}. Cause: {}",
                        stored.storageObjectKey(), deleteException.getMessage());
            }
            throw e;
        }

        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ClinicalDocumentDownload downloadDocument(UUID patientId, UUID documentId) {
        requireId(patientId, "Patient id is required");
        requireId(documentId, "Document id is required");
        ensurePatientExists(patientId);

        ClinicalDocument document = clinicalDocumentRepository.findByIdAndPatient_Id(documentId, patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Clinical document not found"));

        return buildDownload(document);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ClinicalDocumentResponse> findDocumentsByPatient(UUID patientId,
                                                                 ClinicalDocumentType type,
                                                                 int page,
                                                                 int size) {
        requireId(patientId, "Patient id is required");
        ensurePatientExists(patientId);

        Pageable pageable = PageRequest.of(Math.max(0, page), clampPageSize(size), DOCUMENT_ORDER);

        if (type != null) {
            return clinicalDocumentRepository.findByPatient_IdAndType(patientId, type, pageable)
                    .map(mapper::toResponse);
        }
        return clinicalDocumentRepository.findByPatient_Id(patientId, pageable)
                .map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ClinicalDocumentResponse findDocumentById(UUID patientId, UUID documentId) {
        requireId(patientId, "Patient id is required");
        requireId(documentId, "Document id is required");
        ensurePatientExists(patientId);

        ClinicalDocument document = clinicalDocumentRepository.findByIdAndPatient_Id(documentId, patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Clinical document not found"));

        return mapper.toResponse(document);
    }

    @Override
    @Transactional
    public ClinicalDocumentResponse updatePatientVisibility(UUID patientId,
                                                             UUID documentId,
                                                             boolean visible,
                                                             UUID authenticatedUserId) {
        requireId(patientId, "Patient id is required");
        requireId(documentId, "Document id is required");
        requireId(authenticatedUserId, "Authentication is required");
        ensurePatientExists(patientId);

        ClinicalDocument document = clinicalDocumentRepository.findByIdAndPatient_Id(documentId, patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Clinical document not found"));

        User actor = findActiveUser(authenticatedUserId);

        if (document.isPatientVisible() == visible) {
            return mapper.toResponse(document);
        }

        document.updatePatientVisibility(visible, actor, clock.instant());
        return mapper.toResponse(clinicalDocumentRepository.save(document));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PatientClinicalDocumentResponse> findVisibleDocumentsForPatient(
            UUID authenticatedUserId, ClinicalDocumentType type, int page, int size) {
        Patient patient = findPatientByAuthenticatedUser(authenticatedUserId);
        Pageable pageable = PageRequest.of(Math.max(0, page), clampPageSize(size), DOCUMENT_ORDER);

        if (type != null) {
            return clinicalDocumentRepository
                    .findByPatient_IdAndPatientVisibleTrueAndType(patient.getId(), type, pageable)
                    .map(mapper::toPatientResponse);
        }
        return clinicalDocumentRepository.findByPatient_IdAndPatientVisibleTrue(patient.getId(), pageable)
                .map(mapper::toPatientResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PatientClinicalDocumentResponse findVisibleDocumentForPatient(
            UUID authenticatedUserId, UUID documentId) {
        requireId(documentId, "Document id is required");
        Patient patient = findPatientByAuthenticatedUser(authenticatedUserId);
        ClinicalDocument document = clinicalDocumentRepository
                .findByIdAndPatient_IdAndPatientVisibleTrue(documentId, patient.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Clinical document not found"));
        return mapper.toPatientResponse(document);
    }

    @Override
    @Transactional(readOnly = true)
    public ClinicalDocumentDownload downloadVisibleDocumentForPatient(
            UUID authenticatedUserId, UUID documentId) {
        requireId(documentId, "Document id is required");
        Patient patient = findPatientByAuthenticatedUser(authenticatedUserId);
        ClinicalDocument document = clinicalDocumentRepository
                .findByIdAndPatient_IdAndPatientVisibleTrue(documentId, patient.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Clinical document not found"));
        return buildDownload(document);
    }

    private User findActiveUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UnauthorizedException("Authenticated user is not active");
        }
        return user;
    }

    private void ensurePatientExists(UUID patientId) {
        if (!patientRepository.existsById(patientId)) {
            throw new ResourceNotFoundException("Patient not found");
        }
    }

    private Patient findPatientByAuthenticatedUser(UUID authenticatedUserId) {
        requireId(authenticatedUserId, "Authentication is required");
        return patientRepository.findByUser_Id(authenticatedUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found"));
    }

    private ClinicalDocumentDownload buildDownload(ClinicalDocument document) {
        if (!document.hasFile()) {
            throw new ResourceNotFoundException("Clinical document does not have an attached file");
        }

        StoredDocumentContent content = clinicalDocumentStorage.load(document.getStorageObjectKey());
        long size = content.contentLength() > 0
                ? content.contentLength()
                : (document.getFileSize() != null ? document.getFileSize() : 0L);
        String fileName = document.getFileName() != null && !document.getFileName().isBlank()
                ? document.getFileName()
                : "document";
        String contentType = document.getContentType() != null && !document.getContentType().isBlank()
                ? document.getContentType()
                : content.contentType();
        return new ClinicalDocumentDownload(content.content(), fileName, contentType, size);
    }

    private static int clampPageSize(int size) {
        if (size < 1) return 20;
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private static void requireId(UUID id, String message) {
        if (id == null) {
            throw new BadRequestException(message);
        }
    }
}
