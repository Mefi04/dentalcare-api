package com.dentalcare.api.modules.appointments.dto.response;

import java.util.UUID;

public record AdministrativeAppointmentPatientResponse(
        UUID id,
        String code,
        String name,
        String phone) {
}
