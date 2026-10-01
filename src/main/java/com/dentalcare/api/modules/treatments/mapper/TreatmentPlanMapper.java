package com.dentalcare.api.modules.treatments.mapper;

import com.dentalcare.api.modules.treatments.dto.response.TreatmentPlanItemResponse;
import com.dentalcare.api.modules.treatments.dto.response.TreatmentPlanProfessionalResponse;
import com.dentalcare.api.modules.treatments.dto.response.TreatmentPlanResponse;
import com.dentalcare.api.modules.treatments.model.TreatmentPlan;
import com.dentalcare.api.modules.treatments.model.TreatmentPlanItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Component
public class TreatmentPlanMapper {

    public TreatmentPlanResponse toResponse(TreatmentPlan plan) {
        List<TreatmentPlanItemResponse> items = plan.getItems().stream()
                .sorted(Comparator.comparing(TreatmentPlanItem::getPosition))
                .map(this::toItemResponse)
                .toList();
        BigDecimal total = items.stream()
                .map(TreatmentPlanItemResponse::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new TreatmentPlanResponse(
                plan.getId(),
                plan.getPatient().getId(),
                plan.getName(),
                plan.getObservations(),
                new TreatmentPlanProfessionalResponse(
                        plan.getProfessional().getId(), plan.getProfessional().getFullName()),
                plan.getStatus(),
                items,
                total,
                plan.getCreatedAt(),
                plan.getUpdatedAt(),
                plan.getApprovedAt());
    }

    private TreatmentPlanItemResponse toItemResponse(TreatmentPlanItem item) {
        BigDecimal subtotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        return new TreatmentPlanItemResponse(item.getId(), item.getName(), item.getTooth(), item.getQuantity(),
                item.getUnitPrice(), item.getPosition(), subtotal);
    }
}
