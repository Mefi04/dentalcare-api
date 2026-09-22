package com.dentalcare.api.modules.patients.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.patients.dto.request.CreatePatientRequest;
import com.dentalcare.api.modules.patients.dto.request.UpdatePatientRequest;
import com.dentalcare.api.modules.patients.dto.response.PatientResponse;
import com.dentalcare.api.modules.patients.mapper.PatientMapper;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;

@Service
public class PatientServiceImpl implements PatientService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String DUPLICATE_DPI_MESSAGE = "A patient with this DPI already exists";

    private final PatientRepository patientRepository;
    private final PatientMapper patientMapper;
    private final PatientCodeGenerator patientCodeGenerator;
    private final Clock clock;

    public PatientServiceImpl(PatientRepository patientRepository, PatientMapper patientMapper,
                              PatientCodeGenerator patientCodeGenerator, Clock clock) {
        this.patientRepository = patientRepository;
        this.patientMapper = patientMapper;
        this.patientCodeGenerator = patientCodeGenerator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PatientResponse create(CreatePatientRequest request) {
        Patient patient = patientMapper.toEntity(request);
        normalizeAndValidate(patient);
        ensureDpiIsAvailable(patient.getDpi(), null);

        patient.setId(UUID.randomUUID());
        patient.setCode(patientCodeGenerator.nextCode());
        patient.setCreatedAt(clock.instant());
        patient.setUpdatedAt(patient.getCreatedAt());

        return patientMapper.toResponse(savePatient(patient));
    }

    @Override
    @Transactional(readOnly = true)
    public PatientResponse findById(UUID id) {
        return patientMapper.toResponse(findPatient(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PatientResponse> search(int page, int size, String search) {
        if (page < 0) {
            throw new BadRequestException("Page must not be negative");
        }
        if (size < 1) {
            throw new BadRequestException("Size must be at least 1");
        }
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));
        return patientRepository.search(normalizeSearch(search), pageable).map(patientMapper::toResponse);
    }

    @Override
    @Transactional
    public PatientResponse update(UUID id, UpdatePatientRequest request) {
        Patient patient = findPatient(id);
        String currentDpi = patient.getDpi();

        patientMapper.updateEntity(patient, request);
        normalizeAndValidate(patient);
        ensureDpiIsAvailable(patient.getDpi(), currentDpi);
        patient.setUpdatedAt(clock.instant());

        return patientMapper.toResponse(savePatient(patient));
    }

    private Patient findPatient(UUID id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
    }

    private Patient savePatient(Patient patient) {
        try {
            return patientRepository.saveAndFlush(patient);
        } catch (DataIntegrityViolationException exception) {
            // This operation only writes patients; keep concurrent unique-constraint failures safe and meaningful.
            throw new ConflictException("Patient data conflicts with an existing record");
        }
    }

    private void ensureDpiIsAvailable(String dpi, String currentDpi) {
        if (!dpi.equals(currentDpi) && patientRepository.existsByDpi(dpi)) {
            throw new ConflictException(DUPLICATE_DPI_MESSAGE);
        }
    }

    private void normalizeAndValidate(Patient patient) {
        patient.setName(required(patient.getName(), "Name is required"));
        patient.setDpi(normalizeDpi(patient.getDpi()));
        patient.setPhone(required(patient.getPhone(), "Phone is required"));
        patient.setEmail(optional(patient.getEmail()));
        patient.setCity(optional(patient.getCity()));
        patient.setAddress(optional(patient.getAddress()));
        patient.setEmergencyContact(optional(patient.getEmergencyContact()));
        patient.setEmergencyPhone(optional(patient.getEmergencyPhone()));
        patient.setBillingName(optional(patient.getBillingName()));
        patient.setNit(optional(patient.getNit()));
        patient.setBillingAddress(optional(patient.getBillingAddress()));
        patient.setGuardianName(optional(patient.getGuardianName()));
        patient.setGuardianRelationship(optional(patient.getGuardianRelationship()));
        patient.setGuardianPhone(optional(patient.getGuardianPhone()));

        if (patient.getBirthDate() == null) {
            throw new BadRequestException("Birth date is required");
        }
        if (patient.getBirthDate().isAfter(LocalDate.now(clock))) {
            throw new BadRequestException("Birth date cannot be in the future");
        }
        if (patient.getGender() == null) {
            throw new BadRequestException("Gender is required");
        }
        if (patient.getEmail() != null && !patient.getEmail().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new BadRequestException("Email must be valid");
        }
        if (isMinor(patient.getBirthDate())) {
            patient.setGuardianName(required(patient.getGuardianName(), "Guardian name is required for minors"));
            patient.setGuardianRelationship(required(patient.getGuardianRelationship(), "Guardian relationship is required for minors"));
            patient.setGuardianPhone(required(patient.getGuardianPhone(), "Guardian phone is required for minors"));
        }

        patient.setBillingName(patient.getBillingName() != null ? patient.getBillingName() : patient.getName());
        patient.setNit(patient.getNit() != null ? patient.getNit() : "CF");
        patient.setBillingAddress(patient.getBillingAddress() != null ? patient.getBillingAddress() : patient.getAddress());
    }

    private String normalizeDpi(String value) {
        String trimmed = optional(value);
        if (trimmed == null || !trimmed.matches("[0-9 ]+")) {
            throw new BadRequestException("DPI must contain exactly 13 digits");
        }
        String normalized = trimmed.replace(" ", "");
        if (!normalized.matches("\\d{13}")) {
            throw new BadRequestException("DPI must contain exactly 13 digits");
        }
        return normalized;
    }

    private boolean isMinor(LocalDate birthDate) {
        return Period.between(birthDate, LocalDate.now(clock)).getYears() < 18;
    }

    private String required(String value, String message) {
        String normalized = optional(value);
        if (normalized == null) {
            throw new BadRequestException(message);
        }
        return normalized;
    }

    private String optional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeSearch(String search) {
        String value = optional(search);
        if (value != null && value.matches("[0-9 ]+")) {
            return value.replace(" ", "");
        }
        return value;
    }
}
