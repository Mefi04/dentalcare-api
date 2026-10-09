package com.dentalcare.api.modules.appointments.dto.response;

import java.util.UUID;

public record AppointmentWhatsAppDraftResponse(UUID requestId, String phone, String message, String url,
        boolean sentAutomatically) {}
