package com.dentalcare.api.modules.treatments.mapper;

import com.dentalcare.api.modules.treatments.dto.response.TreatmentPlanProfessionalResponse;
import com.dentalcare.api.modules.treatments.dto.response.TreatmentProcedureResponse;
import com.dentalcare.api.modules.treatments.model.TreatmentProcedure;
import org.springframework.stereotype.Component;

@Component
public class TreatmentProcedureMapper {

    public TreatmentProcedureResponse toResponse(TreatmentProcedure procedure) {
        return new TreatmentProcedureResponse(
                procedure.getId(),
                procedure.getTreatmentPlan().getId(),
                procedure.getTreatmentPlanItem().getId(),
                procedure.getPatient().getId(),
                new TreatmentPlanProfessionalResponse(
                        procedure.getProfessional().getId(), procedure.getProfessional().getFullName()),
                procedure.getProcedureName(),
                procedure.getTooth(),
                procedure.getSequenceNumber(),
                procedure.getClinicalObservations(),
                procedure.getCompletionNotes(),
                procedure.getStatus(),
                procedure.getPerformedAt(),
                procedure.getCompletedAt());
    }
}
