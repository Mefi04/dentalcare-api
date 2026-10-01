package com.dentalcare.api.modules.sterilization.mapper;
import com.dentalcare.api.modules.sterilization.dto.response.*;
import com.dentalcare.api.modules.sterilization.model.SterilizationCycle;
import org.springframework.stereotype.Component;
import java.util.Comparator;
@Component
public class SterilizationCycleMapper {
 private final SterilizationProtocolMapper protocolMapper;
 public SterilizationCycleMapper(SterilizationProtocolMapper protocolMapper){this.protocolMapper=protocolMapper;}
 public SterilizationCycleResponse toResponse(SterilizationCycle c){
  var instruments=c.getInstruments().stream().sorted(Comparator.comparing(i->i.getCode())).map(i->new SterilizationInstrumentResponse(i.getId(),i.getCode(),i.getName())).toList();
  return new SterilizationCycleResponse(c.getId(),c.getCode(),protocolMapper.toResponse(c.getProtocol()),
   new SterilizationResponsibleResponse(c.getResponsible().getId(),c.getResponsible().getFullName()),c.getStatus(),c.getObservations(),c.getStartedAt(),c.getReleasedAt(),instruments);
 }
}
