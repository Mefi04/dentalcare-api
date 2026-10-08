package com.dentalcare.api.modules.medicalhistory.dto.response;

import com.dentalcare.api.modules.medicalhistory.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public record MedicalHistoryTemplateResponse(UUID templateId,String code,String name,UUID versionId,int versionNumber,
        String title,MedicalHistoryTemplateStatus status,Instant publishedAt,List<Section> sections) {
    public record Section(UUID id,String key,String title,String description,int order,List<Question> questions) {}
    public record Question(UUID id,String key,String prompt,MedicalHistoryAnswerType answerType,int order,
                           boolean required,boolean notesAllowed,Integer maxLength,BigDecimal minValue,BigDecimal maxValue,
                           JsonNode choices,JsonNode conditionalNoteRule) {}
}
