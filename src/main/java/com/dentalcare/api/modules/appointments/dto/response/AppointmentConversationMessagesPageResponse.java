package com.dentalcare.api.modules.appointments.dto.response;

import java.util.List;

public record AppointmentConversationMessagesPageResponse(List<AppointmentRequestMessageResponse> items,
        String nextCursor, boolean hasMore, int pageSize) {}
