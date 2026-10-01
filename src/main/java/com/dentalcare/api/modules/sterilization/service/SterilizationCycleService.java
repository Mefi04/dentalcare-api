package com.dentalcare.api.modules.sterilization.service;
import com.dentalcare.api.modules.sterilization.dto.request.CreateSterilizationCycleRequest;
import com.dentalcare.api.modules.sterilization.dto.response.SterilizationCycleResponse;
import com.dentalcare.api.modules.sterilization.model.SterilizationCycleStatus;
import org.springframework.data.domain.Page;
import java.time.Instant;
import java.util.UUID;
public interface SterilizationCycleService {
 Page<SterilizationCycleResponse> findAll(UUID protocolId,SterilizationCycleStatus status,Instant from,Instant to,int page,int size);
 SterilizationCycleResponse findById(UUID id);
 SterilizationCycleResponse create(CreateSterilizationCycleRequest request,UUID responsibleUserId);
 SterilizationCycleResponse updateStatus(UUID id,SterilizationCycleStatus status);
}
