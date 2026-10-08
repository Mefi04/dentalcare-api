package com.dentalcare.api.modules.prescriptions.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.audit.service.AuditActions;
import com.dentalcare.api.modules.audit.service.AuditService;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.notifications.model.NotificationEventType;
import com.dentalcare.api.modules.notifications.service.PatientNotificationPublisher;
import com.dentalcare.api.modules.prescriptions.dto.request.CreatePrescriptionRequest;
import com.dentalcare.api.modules.prescriptions.dto.response.PrescriptionResponse;
import com.dentalcare.api.modules.prescriptions.mapper.PrescriptionMapper;
import com.dentalcare.api.modules.prescriptions.model.Prescription;
import com.dentalcare.api.modules.prescriptions.model.PrescriptionStatus;
import com.dentalcare.api.modules.prescriptions.repository.PrescriptionRepository;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.UUID;

@Service
public class PrescriptionServiceImpl implements PrescriptionService {
    @org.springframework.beans.factory.annotation.Autowired(required = false) private AuditService auditService;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort ORDER = Sort.by(Sort.Order.desc("issuedAt"), Sort.Order.desc("id"));

    private final PrescriptionRepository prescriptions;
    private final PatientRepository patients;
    private final UserRepository users;
    private final PrescriptionMapper mapper;
    private final PatientNotificationPublisher notificationPublisher;
    private final Clock clock;

    public PrescriptionServiceImpl(PrescriptionRepository prescriptions, PatientRepository patients,
            UserRepository users, PrescriptionMapper mapper, PatientNotificationPublisher notificationPublisher,
            Clock clock) {
        this.prescriptions = prescriptions;
        this.patients = patients;
        this.users = users;
        this.mapper = mapper;
        this.notificationPublisher = notificationPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PrescriptionResponse create(UUID patientId, UUID professionalId, CreatePrescriptionRequest request) {
        requireId(patientId, "Patient id is required");
        requireId(professionalId, "Authentication is required");
        if (request == null) {
            throw new BadRequestException("Prescription data is required");
        }
        Patient patient = patients.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
        User professional = findActiveDentist(professionalId);
        Prescription value = new Prescription(UUID.randomUUID(), patient, professional,
                required(request.medication()), required(request.presentation()), required(request.dosage()),
                required(request.frequency()), required(request.duration()), optional(request.instructions()),
                clock.instant(), PrescriptionStatus.ISSUED);
        var saved=prescriptions.saveAndFlush(value);
        notificationPublisher.publish(patientId, NotificationEventType.PRESCRIPTION_ISSUED,
                "Nueva receta disponible", "Se emitió una receta para ti. Consulta Recetas para ver los detalles.");
        if(auditService!=null)auditService.success(AuditActions.PRESCRIPTION_ISSUED,"PRESCRIPTIONS","Prescription",saved.getId(),professionalId);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PrescriptionResponse> findByPatient(UUID patientId, int page, int size) {
        requireId(patientId, "Patient id is required");
        validatePage(page, size);
        if (!patients.existsById(patientId)) {
            throw new ResourceNotFoundException("Patient not found");
        }
        return prescriptions.findByPatient_Id(patientId, pageable(page, size)).map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PrescriptionResponse findById(UUID id) {
        requireId(id, "Prescription id is required");
        return prescriptions.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PrescriptionResponse> findMine(UUID userId, int page, int size) {
        Patient patient = patientForUser(userId);
        validatePage(page, size);
        return prescriptions.findByPatient_Id(patient.getId(), pageable(page, size)).map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PrescriptionResponse findMineById(UUID userId, UUID id) {
        Patient patient = patientForUser(userId);
        requireId(id, "Prescription id is required");
        return prescriptions.findByIdAndPatient_Id(id, patient.getId()).map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription not found"));
    }

    private Patient patientForUser(UUID userId) {
        requireId(userId, "Authentication is required");
        return patients.findByUser_Id(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
    }

    private User findActiveDentist(UUID id) {
        User user = users.findWithRolesById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Professional not found"));
        boolean valid = user.getStatus() == UserStatus.ACTIVE && user.getRoles().stream()
                .anyMatch(role -> role.isActive() && "DENTIST".equals(role.getCode()));
        if (!valid) {
            throw new ConflictException("Professional is not an active dentist");
        }
        return user;
    }

    private PageRequest pageable(int page, int size) {
        return PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), ORDER);
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("Page must be at least 0");
        }
        if (size < 1) {
            throw new BadRequestException("Size must be at least 1");
        }
    }

    private void requireId(UUID id, String message) {
        if (id == null) {
            throw new BadRequestException(message);
        }
    }

    private String required(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Required prescription text must not be blank");
        }
        return value.trim();
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
