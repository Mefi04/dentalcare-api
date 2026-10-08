package com.dentalcare.api.modules.medicalhistory.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;

public record SaveMedicalHistoryAnswersRequest(@PositiveOrZero long lockVersion,
        @NotNull @Size(max=300) List<@Valid Answer> answers) {
    public record Answer(@NotNull UUID questionId,@NotNull JsonNode value,@Size(max=2000) String note) {}
}
