package com.dentalcare.api.modules.prescriptions.mapper;

import com.dentalcare.api.modules.prescriptions.dto.response.PrescriptionPatientResponse;
import com.dentalcare.api.modules.prescriptions.dto.response.PrescriptionProfessionalResponse;
import com.dentalcare.api.modules.prescriptions.dto.response.PrescriptionResponse;
import com.dentalcare.api.modules.prescriptions.model.Prescription;
import org.springframework.stereotype.Component;

@Component
public class PrescriptionMapper {
    public PrescriptionResponse toResponse(Prescription value) {
        return new PrescriptionResponse(value.getId(),
                new PrescriptionPatientResponse(value.getPatient().getId(), value.getPatient().getCode(),
                        value.getPatient().getName()),
                new PrescriptionProfessionalResponse(value.getProfessional().getId(),
                        value.getProfessional().getFullName()),
                value.getMedication(), value.getPresentation(), value.getDosage(), value.getFrequency(),
                value.getDuration(), value.getInstructions(), value.getIssuedAt(), value.getStatus());
    }
}
