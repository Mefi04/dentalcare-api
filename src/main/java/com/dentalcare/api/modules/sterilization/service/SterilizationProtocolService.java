package com.dentalcare.api.modules.sterilization.service;
import com.dentalcare.api.modules.sterilization.dto.request.*;
import com.dentalcare.api.modules.sterilization.dto.response.SterilizationProtocolResponse;
import org.springframework.data.domain.Page;
import java.util.UUID;
public interface SterilizationProtocolService {
 Page<SterilizationProtocolResponse> findAll(String search,Boolean active,int page,int size);
 SterilizationProtocolResponse findById(UUID id);
 SterilizationProtocolResponse create(CreateSterilizationProtocolRequest request);
 SterilizationProtocolResponse update(UUID id,UpdateSterilizationProtocolRequest request);
 SterilizationProtocolResponse updateStatus(UUID id,boolean active);
}
