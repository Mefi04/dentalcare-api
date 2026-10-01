package com.dentalcare.api.modules.clinicalrecords.dto.response;

import com.dentalcare.api.modules.clinicalrecords.model.DentitionType;
import com.dentalcare.api.modules.clinicalrecords.model.ToothFinding;
import com.dentalcare.api.modules.clinicalrecords.model.ToothSurface;

import java.time.Instant;
import java.util.UUID;

public record OdontogramFindingResponse(
        UUID id,
        UUID patientId,
        UUID attentionId,
        ClinicalProfessionalResponse author,
        DentitionType dentition,
        String toothCode,
        ToothSurface surface,
        ToothFinding finding,
        String observation,
        Instant createdAt) {
}
