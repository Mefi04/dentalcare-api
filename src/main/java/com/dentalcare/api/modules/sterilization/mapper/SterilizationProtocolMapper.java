package com.dentalcare.api.modules.sterilization.mapper;
import com.dentalcare.api.modules.sterilization.dto.response.SterilizationProtocolResponse;
import com.dentalcare.api.modules.sterilization.model.SterilizationProtocol;
import org.springframework.stereotype.Component;
@Component
public class SterilizationProtocolMapper {
 public SterilizationProtocolResponse toResponse(SterilizationProtocol p){
  return new SterilizationProtocolResponse(p.getId(),p.getName(),p.getMethod(),p.getDescription(),p.getInstructions(),p.isActive(),p.getCreatedAt(),p.getUpdatedAt());
 }
}
