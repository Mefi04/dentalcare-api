package com.dentalcare.api.modules.sterilization.dto.response;
import com.dentalcare.api.modules.sterilization.model.SterilizationCycleStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public record SterilizationCycleResponse(UUID id,String code,SterilizationProtocolResponse protocol,
 SterilizationResponsibleResponse responsible,SterilizationCycleStatus status,String observations,
 Instant startedAt,Instant releasedAt,List<SterilizationInstrumentResponse> instruments) {}
