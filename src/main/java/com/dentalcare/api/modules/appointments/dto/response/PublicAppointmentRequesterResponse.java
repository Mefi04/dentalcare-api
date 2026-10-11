package com.dentalcare.api.modules.appointments.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Contact details supplied for a public request; visible only to authorized clinic staff")
public record PublicAppointmentRequesterResponse(
        String fullName,
        String cui,
        String phone,
        String email,
        String reason,
        LocalDate birthDate,
        String gender,
        String alternativeId,
        String guardianName,
        String guardianRelationship,
        String guardianPhone,
        String department,
        String municipality,
        String address,
        String emergencyName,
        String emergencyPhone,
        String nit,
        String billingName,
        String billingAddress,
        Boolean privacyAccepted
) {
    public PublicAppointmentRequesterResponse(
            String fullName,
            String cui,
            String phone,
            String email,
            String reason) {
        this(fullName, cui, phone, email, reason,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, true);
    }
}
