package com.dentalcare.api.modules.sterilization.dto.response;
import com.dentalcare.api.modules.sterilization.model.SterilizationMethod;
import java.time.Instant;
import java.util.UUID;
public record SterilizationProtocolResponse(UUID id,String name,SterilizationMethod method,String description,
 String instructions,boolean active,Instant createdAt,Instant updatedAt) {}
