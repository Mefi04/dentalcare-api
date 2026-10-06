package com.dentalcare.api.modules.publicinfo.service;

import com.dentalcare.api.modules.publicinfo.mapper.PublicInformationMapper;
import com.dentalcare.api.modules.settings.dto.response.ClinicSettingsResponse;
import com.dentalcare.api.modules.settings.dto.response.ProcedureCatalogItemResponse;
import com.dentalcare.api.modules.settings.model.ProcedureCatalogItemStatus;
import com.dentalcare.api.modules.settings.service.ClinicSettingsService;
import com.dentalcare.api.modules.settings.service.ProcedureCatalogService;
import com.dentalcare.api.modules.users.service.ProfessionalPublicProfileService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.*;

class PublicInformationServiceImplTests {
    @Test void clinicProjectionUsesOnlyPublicFields() {
        var clinic = mock(ClinicSettingsService.class);
        var catalog = mock(ProcedureCatalogService.class);
        var profiles = mock(ProfessionalPublicProfileService.class);
        var service = new PublicInformationServiceImpl(clinic, catalog, new PublicInformationMapper(), profiles);
        var now = Instant.parse("2026-10-04T12:00:00Z");
        when(clinic.get()).thenReturn(new ClinicSettingsResponse((short) 1, "DentalCare", "548796-2", "22224500",
                "info@dentalcare.test", "Zone 10", "Guatemala", "Mon-Fri", "DC", UUID.randomUUID(), now, now));

        assertThat(service.getClinic()).hasNoNullFieldsOrProperties().extracting("tradeName", "phone", "email", "address", "city", "businessHours")
                .containsExactly("DentalCare", "22224500", "info@dentalcare.test", "Zone 10", "Guatemala", "Mon-Fri");
    }

    @Test void servicesProjectionUsesTheActiveCatalogReadModelOnly() {
        var clinic = mock(ClinicSettingsService.class);
        var catalog = mock(ProcedureCatalogService.class);
        var profiles = mock(ProfessionalPublicProfileService.class);
        var service = new PublicInformationServiceImpl(clinic, catalog, new PublicInformationMapper(), profiles);
        var actor = UUID.randomUUID();
        var now = Instant.parse("2026-10-04T12:00:00Z");
        when(catalog.findActive()).thenReturn(List.of(new ProcedureCatalogItemResponse(UUID.randomUUID(), "CLEANING", "Cleaning",
                "Prevention", 30, new BigDecimal("150.00"), ProcedureCatalogItemStatus.ACTIVE, actor, actor, now, now)));

        assertThat(service.getServices()).extracting("code", "name", "category", "durationMinutes")
                .containsExactly(tuple("CLEANING", "Cleaning", "Prevention", 30));
        verify(catalog).findActive();
        verifyNoMoreInteractions(catalog);
    }
}
