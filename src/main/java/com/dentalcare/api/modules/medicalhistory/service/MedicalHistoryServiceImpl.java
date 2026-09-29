package com.dentalcare.api.modules.medicalhistory.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.medicalhistory.dto.request.UpdateMedicalHistoryRequest;
import com.dentalcare.api.modules.medicalhistory.dto.response.MedicalHistoryResponse;
import com.dentalcare.api.modules.medicalhistory.mapper.MedicalHistoryMapper;
import com.dentalcare.api.modules.medicalhistory.model.MedicalHistory;
import com.dentalcare.api.modules.medicalhistory.repository.MedicalHistoryRepository;
import com.dentalcare.api.modules.patients.dto.response.PatientHealthResponse;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class MedicalHistoryServiceImpl implements MedicalHistoryService {

    private static final int MAX_ITEMS = 100;
    private static final int MAX_ITEM_LENGTH = 200;
    private static final int MAX_OBSERVATIONS_LENGTH = 4000;

    private final MedicalHistoryRepository medicalHistoryRepository;
    private final PatientRepository patientRepository;
    private final MedicalHistoryMapper medicalHistoryMapper;
    private final Clock clock;

    public MedicalHistoryServiceImpl(MedicalHistoryRepository medicalHistoryRepository,
                                     PatientRepository patientRepository,
                                     MedicalHistoryMapper medicalHistoryMapper,
                                     Clock clock) {
        this.medicalHistoryRepository = medicalHistoryRepository;
        this.patientRepository = patientRepository;
        this.medicalHistoryMapper = medicalHistoryMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public MedicalHistoryResponse findByPatientId(UUID patientId) {
        requirePatientId(patientId);
        ensurePatientExists(patientId);
        MedicalHistory history = medicalHistoryRepository.findByPatient_Id(patientId).orElse(null);
        return medicalHistoryMapper.toResponse(patientId, history);
    }

    @Override
    @Transactional
    public MedicalHistoryResponse update(UUID patientId, UpdateMedicalHistoryRequest request) {
        requirePatientId(patientId);
        if (request == null) {
            throw new BadRequestException("Medical history is required");
        }

        Patient patient = patientRepository.findByIdForUpdate(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
        Instant now = clock.instant();
        MedicalHistory history = medicalHistoryRepository.findByPatient_Id(patientId)
                .orElseGet(() -> new MedicalHistory(UUID.randomUUID(), patient, now, now));

        history.replaceAllergies(normalizeItems(request.allergies(), "Allergies"));
        history.replaceCurrentMedications(normalizeItems(request.currentMedications(), "Current medications"));
        history.replaceRelevantConditions(normalizeItems(request.relevantConditions(), "Relevant conditions"));
        history.setObservations(normalizeObservations(request.observations()));
        history.setUpdatedAt(now);

        try {
            return medicalHistoryMapper.toResponse(patientId, medicalHistoryRepository.saveAndFlush(history));
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Medical history conflicts with an existing record");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PatientHealthResponse findForAuthenticatedPatient(UUID authenticatedUserId) {
        if (authenticatedUserId == null) {
            throw new BadRequestException("Authenticated user id is required");
        }
        Patient patient = patientRepository.findByUser_Id(authenticatedUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
        MedicalHistory history = medicalHistoryRepository.findByPatient_Id(patient.getId()).orElse(null);
        return medicalHistoryMapper.toPatientHealthResponse(history);
    }

    private void ensurePatientExists(UUID patientId) {
        if (!patientRepository.existsById(patientId)) {
            throw new ResourceNotFoundException("Patient not found");
        }
    }

    private void requirePatientId(UUID patientId) {
        if (patientId == null) {
            throw new BadRequestException("Patient id is required");
        }
    }

    private List<String> normalizeItems(List<String> values, String fieldName) {
        if (values == null) {
            throw new BadRequestException(fieldName + " are required");
        }
        if (values.size() > MAX_ITEMS) {
            throw new BadRequestException(fieldName + " must not contain more than " + MAX_ITEMS + " entries");
        }

        Map<String, String> uniqueValues = new LinkedHashMap<>();
        for (String value : values) {
            String normalized = normalizeText(value);
            if (normalized == null) {
                throw new BadRequestException(fieldName + " must not contain blank entries");
            }
            if (normalized.length() > MAX_ITEM_LENGTH) {
                throw new BadRequestException(fieldName + " entries must not exceed " + MAX_ITEM_LENGTH + " characters");
            }
            uniqueValues.putIfAbsent(normalized.toLowerCase(Locale.ROOT), normalized);
        }
        return new ArrayList<>(uniqueValues.values());
    }

    private String normalizeObservations(String value) {
        String normalized = normalizeText(value);
        if (normalized != null && normalized.length() > MAX_OBSERVATIONS_LENGTH) {
            throw new BadRequestException("Observations must not exceed " + MAX_OBSERVATIONS_LENGTH + " characters");
        }
        return normalized;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
