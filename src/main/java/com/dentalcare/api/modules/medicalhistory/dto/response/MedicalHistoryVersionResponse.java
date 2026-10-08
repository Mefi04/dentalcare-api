package com.dentalcare.api.modules.medicalhistory.dto.response;

import com.dentalcare.api.modules.medicalhistory.model.MedicalHistoryVersionSource;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record MedicalHistoryVersionResponse(UUID id,UUID patientId,int versionNumber,MedicalHistoryVersionSource source,
        UUID questionnaireId,Integer revisionNumber,UUID validatorId,String validatorName,Instant validatedAt,
        JsonNode snapshot,boolean current) {}
