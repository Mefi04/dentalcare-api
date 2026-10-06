package com.dentalcare.api.modules.treatments.mapper;

import com.dentalcare.api.modules.treatments.dto.response.PatientTreatmentItemStatus;
import com.dentalcare.api.modules.treatments.dto.response.PatientTreatmentPlanItemResponse;
import com.dentalcare.api.modules.treatments.dto.response.PatientTreatmentPlanResponse;
import com.dentalcare.api.modules.treatments.dto.response.PatientTreatmentProcedureResponse;
import com.dentalcare.api.modules.treatments.model.TreatmentPlan;
import com.dentalcare.api.modules.treatments.model.TreatmentPlanItem;
import com.dentalcare.api.modules.treatments.model.TreatmentProcedure;
import com.dentalcare.api.modules.treatments.model.TreatmentProcedureStatus;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class PatientTreatmentPlanMapper {

    public PatientTreatmentPlanResponse toResponse(TreatmentPlan plan,
                                                   List<TreatmentProcedure> procedures) {
        Map<UUID, List<TreatmentProcedure>> byItem = procedures.stream()
                .collect(Collectors.groupingBy(value -> value.getTreatmentPlanItem().getId()));
        List<PatientTreatmentPlanItemResponse> items = plan.getItems().stream()
                .sorted(Comparator.comparing(TreatmentPlanItem::getPosition))
                .map(item -> toItem(item, byItem.getOrDefault(item.getId(), List.of())))
                .toList();
        long planned = items.stream().mapToLong(PatientTreatmentPlanItemResponse::plannedQuantity).sum();
        long completed = items.stream().mapToLong(PatientTreatmentPlanItemResponse::completedQuantity).sum();
        return new PatientTreatmentPlanResponse(
                plan.getId(), plan.getName(), plan.getStatus(), plan.getProfessional().getFullName(),
                plan.getCreatedAt(), plan.getApprovedAt(), planned, completed,
                percentage(completed, planned), items);
    }

    private PatientTreatmentPlanItemResponse toItem(TreatmentPlanItem item,
                                                    List<TreatmentProcedure> procedures) {
        List<PatientTreatmentProcedureResponse> executions = procedures.stream()
                .sorted(Comparator.comparing(TreatmentProcedure::getPerformedAt)
                        .thenComparing(TreatmentProcedure::getId))
                .map(value -> new PatientTreatmentProcedureResponse(
                        value.getId(), value.getSequenceNumber(), value.getStatus(),
                        value.getPerformedAt(), value.getCompletedAt()))
                .toList();
        long completed = procedures.stream()
                .filter(value -> value.getStatus() == TreatmentProcedureStatus.COMPLETED).count();
        long inProgress = procedures.stream()
                .filter(value -> value.getStatus() == TreatmentProcedureStatus.IN_PROGRESS).count();
        PatientTreatmentItemStatus status = completed >= item.getQuantity()
                ? PatientTreatmentItemStatus.COMPLETED
                : completed > 0 || inProgress > 0
                ? PatientTreatmentItemStatus.IN_PROGRESS
                : PatientTreatmentItemStatus.PENDING;
        return new PatientTreatmentPlanItemResponse(
                item.getId(), item.getName(), item.getTooth(), item.getQuantity(), completed,
                inProgress, percentage(completed, item.getQuantity()), status, executions);
    }

    private int percentage(long completed, long planned) {
        return planned == 0 ? 0 : (int) Math.min(100, completed * 100 / planned);
    }
}
