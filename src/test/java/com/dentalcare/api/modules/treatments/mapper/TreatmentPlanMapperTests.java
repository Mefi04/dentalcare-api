package com.dentalcare.api.modules.treatments.mapper;

import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.treatments.model.TreatmentPlan;
import com.dentalcare.api.modules.treatments.model.TreatmentPlanItem;
import com.dentalcare.api.modules.treatments.model.TreatmentPlanStatus;
import com.dentalcare.api.modules.users.model.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TreatmentPlanMapperTests {

    private final TreatmentPlanMapper mapper = new TreatmentPlanMapper();

    @Test
    void mapsSafeProfessionalFieldsOrderedItemsAndDerivedAmounts() {
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        User professional = new User();
        professional.setId(UUID.randomUUID());
        professional.setFullName("Dra. Ana Pérez");
        professional.setEmail("private@example.test");
        TreatmentPlan plan = new TreatmentPlan(UUID.randomUUID(), patient, professional, "Plan", null,
                TreatmentPlanStatus.DRAFT, Instant.EPOCH, Instant.EPOCH);
        plan.replaceItems(List.of(
                new TreatmentPlanItem(UUID.randomUUID(), "Segundo", null, 2, new BigDecimal("10.25"), 1),
                new TreatmentPlanItem(UUID.randomUUID(), "Primero", "16", 3, new BigDecimal("20.10"), 0)));

        var response = mapper.toResponse(plan);

        assertThat(response.professional().id()).isEqualTo(professional.getId());
        assertThat(response.professional().fullName()).isEqualTo("Dra. Ana Pérez");
        assertThat(response.items()).extracting(item -> item.name()).containsExactly("Primero", "Segundo");
        assertThat(response.items().get(0).subtotal()).isEqualByComparingTo("60.30");
        assertThat(response.items().get(1).tooth()).isNull();
        assertThat(response.total()).isEqualByComparingTo("80.80");
        assertThat(response.approvedAt()).isNull();
    }

    @Test
    void mapsApprovalTimestamp() {
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        User professional = new User();
        professional.setId(UUID.randomUUID());
        professional.setFullName("Dentist");
        TreatmentPlan plan = new TreatmentPlan(UUID.randomUUID(), patient, professional, "Plan", null,
                TreatmentPlanStatus.DRAFT, Instant.EPOCH, Instant.EPOCH);
        plan.addItem(new TreatmentPlanItem(UUID.randomUUID(), "Consulta", null, 1,
                new BigDecimal("100.00"), 0));
        Instant approvedAt = Instant.parse("2026-10-01T12:00:00Z");
        plan.approve(approvedAt);

        assertThat(mapper.toResponse(plan).approvedAt()).isEqualTo(approvedAt);
    }
}
