package com.dentalcare.api.modules.appointments.dto.response;

import java.util.UUID;

public record FirstAppointmentReceipt(UUID requestId, boolean confirmed, String message) { }
