package com.dentalcare.api.modules.appointments.repository;

import com.dentalcare.api.modules.appointments.model.AppointmentPublicConversationToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentPublicConversationTokenRepository
        extends JpaRepository<AppointmentPublicConversationToken, String> {
}
