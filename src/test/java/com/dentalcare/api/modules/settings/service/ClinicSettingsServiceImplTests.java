package com.dentalcare.api.modules.settings.service;

import com.dentalcare.api.modules.settings.dto.request.UpdateClinicSettingsRequest;
import com.dentalcare.api.modules.settings.mapper.ClinicSettingsMapper;
import com.dentalcare.api.modules.settings.model.ClinicSettings;
import com.dentalcare.api.modules.settings.repository.ClinicSettingsRepository;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ClinicSettingsServiceImplTests {
    private ClinicSettingsRepository repository;
    private UserRepository users;
    private ClinicSettings settings;
    private ClinicSettingsServiceImpl service;
    private final UUID actor=UUID.randomUUID();

    @BeforeEach void setUp(){
        repository=mock(ClinicSettingsRepository.class); users=mock(UserRepository.class); settings=mock(ClinicSettings.class);
        when(settings.getId()).thenReturn((short)1); when(repository.findById((short)1)).thenReturn(Optional.of(settings));
        when(repository.saveAndFlush(settings)).thenReturn(settings); when(users.existsById(actor)).thenReturn(true);
        service=new ClinicSettingsServiceImpl(repository,users,new ClinicSettingsMapper(),Clock.fixed(Instant.parse("2026-10-04T12:00:00Z"),ZoneOffset.UTC));
    }
    @Test void getReadsTheSingleton(){assertThat(service.get().id()).isEqualTo((short)1); verify(repository).findById((short)1);}
    @Test void updateNormalizesValuesAndUsesAuthenticatedActor(){
        var request=new UpdateClinicSettingsRequest(" DentalCare "," 548796-2 "," +502 2222-4500 "," INFO@EXAMPLE.COM ",
                " Zone 10 "," Guatemala "," Monday to Friday "," dc- ");
        service.update(request,actor);
        verify(settings).setTradeName("DentalCare"); verify(settings).setNit("548796-2");
        verify(settings).setEmail("info@example.com"); verify(settings).setReceiptPrefix("DC-");
        verify(settings).setUpdatedBy(actor); verify(settings).setUpdatedAt(Instant.parse("2026-10-04T12:00:00Z"));
    }
}
