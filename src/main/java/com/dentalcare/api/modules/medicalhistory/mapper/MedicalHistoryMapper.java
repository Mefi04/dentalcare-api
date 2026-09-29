package com.dentalcare.api.modules.medicalhistory.mapper;

import com.dentalcare.api.modules.medicalhistory.dto.response.MedicalHistoryResponse;
import com.dentalcare.api.modules.medicalhistory.model.MedicalHistory;
import com.dentalcare.api.modules.patients.dto.response.PatientHealthResponse;
import com.dentalcare.api.modules.patients.dto.response.PatientHealthStatus;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
public class MedicalHistoryMapper {

    public MedicalHistoryResponse toResponse(UUID patientId, MedicalHistory history) {
        if (history == null) {
            return new MedicalHistoryResponse(patientId, List.of(), List.of(), List.of(), null,
                    null, null, PatientHealthStatus.EMPTY);
        }
        return new MedicalHistoryResponse(
                patientId,
                sorted(history.getAllergies()),
                sorted(history.getCurrentMedications()),
                sorted(history.getRelevantConditions()),
                history.getObservations(),
                history.getCreatedAt(),
                history.getUpdatedAt(),
                status(history));
    }

    public PatientHealthResponse toPatientHealthResponse(MedicalHistory history) {
        if (history == null) {
            return new PatientHealthResponse(List.of(), List.of(), List.of(), List.of(), null,
                    null, PatientHealthStatus.EMPTY);
        }
        return new PatientHealthResponse(
                sorted(history.getAllergies()),
                sorted(history.getCurrentMedications()),
                sorted(history.getRelevantConditions()),
                List.of(),
                history.getObservations(),
                history.getUpdatedAt(),
                status(history));
    }

    private PatientHealthStatus status(MedicalHistory history) {
        boolean empty = history.getAllergies().isEmpty()
                && history.getCurrentMedications().isEmpty()
                && history.getRelevantConditions().isEmpty()
                && history.getObservations() == null;
        return empty ? PatientHealthStatus.EMPTY : PatientHealthStatus.UPDATED;
    }

    private List<String> sorted(Iterable<String> values) {
        java.util.ArrayList<String> sorted = new java.util.ArrayList<>();
        values.forEach(sorted::add);
        sorted.sort(Comparator.comparing((String value) -> value.toLowerCase(Locale.ROOT))
                .thenComparing(Comparator.naturalOrder()));
        return List.copyOf(sorted);
    }
}
