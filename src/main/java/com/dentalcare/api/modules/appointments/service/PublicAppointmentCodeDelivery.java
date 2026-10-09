package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.modules.appointments.dto.request.PublicVerificationChannelRequest.Channel;
import java.time.Duration;

public interface PublicAppointmentCodeDelivery {
    void deliver(Channel channel, String destination, String code, Duration validity);
}
