package com.dentalcare.api.modules.clinicalrecords.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.exception.UnauthorizedException;
import com.dentalcare.api.modules.clinicalrecords.dto.request.CreateClinicalDocumentRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalDocumentResponse;
import com.dentalcare.api.modules.clinicalrecords.mapper.ClinicalDocumentMapper;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDocument;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDocumentType;
import com.dentalcare.api.modules.clinicalrecords.repository.ClinicalDocumentRepository;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class ClinicalDocumentServiceImpl implements ClinicalDocumentService {

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
    private final Clock clock;

    public ClinicalDocumentServiceImpl(ClinicalDocumentRepository clinicalDocumentRepository,
                                       PatientRepository patientRepository,
                                       UserRepository userRepository,
                                       ClinicalDocumentMapper mapper,
                                       Clock clock) {
        this.clinicalDocumentRepository = clinicalDocumentRepository;
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
        this.mapper = mapper;
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
