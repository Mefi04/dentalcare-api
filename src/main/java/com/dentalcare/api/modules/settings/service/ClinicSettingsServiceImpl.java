package com.dentalcare.api.modules.settings.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.audit.service.AuditActions;
import com.dentalcare.api.modules.audit.service.AuditService;
import com.dentalcare.api.modules.settings.dto.request.UpdateClinicSettingsRequest;
import com.dentalcare.api.modules.settings.dto.response.ClinicSettingsResponse;
import com.dentalcare.api.modules.settings.mapper.ClinicSettingsMapper;
import com.dentalcare.api.modules.settings.model.ClinicSettings;
import com.dentalcare.api.modules.settings.repository.ClinicSettingsRepository;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;

@Service
public class ClinicSettingsServiceImpl implements ClinicSettingsService {
    @org.springframework.beans.factory.annotation.Autowired(required = false) private AuditService auditService;
    private final ClinicSettingsRepository repository;
    private final UserRepository userRepository;
    private final ClinicSettingsMapper mapper;
    private final Clock clock;

    public ClinicSettingsServiceImpl(ClinicSettingsRepository repository, UserRepository userRepository,
                                     ClinicSettingsMapper mapper, Clock clock) {
        this.repository=repository; this.userRepository=userRepository; this.mapper=mapper; this.clock=clock;
    }

    @Override @Transactional(readOnly=true)
    public ClinicSettingsResponse get() { return mapper.toResponse(findSingleton()); }

    @Override @Transactional
    public ClinicSettingsResponse update(UpdateClinicSettingsRequest request, UUID actorId) {
        requireActor(actorId);
        ClinicSettings settings=findSingleton();
        settings.setTradeName(required(request.tradeName()));
        settings.setNit(required(request.nit()).replace(" ", "").toUpperCase(Locale.ROOT));
        settings.setPhone(required(request.phone()));
        settings.setEmail(optionalLower(request.email()));
        settings.setAddress(optional(request.address()));
        settings.setCity(optional(request.city()));
        settings.setBusinessHours(optional(request.businessHours()));
        settings.setReceiptPrefix(optionalUpper(request.receiptPrefix()));
        settings.setUpdatedBy(actorId);
        settings.setUpdatedAt(clock.instant());
        var response=mapper.toResponse(repository.saveAndFlush(settings));
        if(auditService!=null)auditService.success(AuditActions.SETTINGS_CLINIC_UPDATED,"SETTINGS","ClinicSettings",null,actorId);
        return response;
    }

    private ClinicSettings findSingleton() {
        return repository.findById(ClinicSettings.SINGLETON_ID)
                .orElseThrow(() -> new ResourceNotFoundException("Clinic settings not found"));
    }
    private void requireActor(UUID actorId) {
        if (actorId==null) throw new BadRequestException("Authenticated actor is required");
        if (!userRepository.existsById(actorId)) throw new BadRequestException("Authenticated actor is invalid");
    }
    private String required(String value){return value.trim();}
    private String optional(String value){if(value==null)return null; String v=value.trim(); return v.isEmpty()?null:v;}
    private String optionalLower(String value){String v=optional(value); return v==null?null:v.toLowerCase(Locale.ROOT);}
    private String optionalUpper(String value){String v=optional(value); return v==null?null:v.toUpperCase(Locale.ROOT);}
}
