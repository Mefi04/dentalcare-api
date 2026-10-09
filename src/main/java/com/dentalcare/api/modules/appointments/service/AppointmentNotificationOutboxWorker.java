package com.dentalcare.api.modules.appointments.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AppointmentNotificationOutboxWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppointmentNotificationOutboxWorker.class);
    private final AppointmentNotificationOutboxService outbox;
    private final AppointmentOutboxProperties properties;

    public AppointmentNotificationOutboxWorker(AppointmentNotificationOutboxService outbox,
            AppointmentOutboxProperties properties) {
        this.outbox = outbox;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${dentalcare.appointment-outbox.poll-delay:PT5S}")
    public void processDueEvents() {
        if (properties.encryptionKey().isBlank()) return;
        for (int count = 0; count < properties.batchSize(); count++) {
            var event = outbox.claimNext();
            if (event == null) return;
            if (event.getAttempts() > properties.maxAttempts()) {
                outbox.markFailed(event.getId(), "MAX_ATTEMPTS_EXCEEDED");
                continue;
            }
            try {
                outbox.dispatch(event);
            } catch (RuntimeException exception) {
                LOGGER.warn("Appointment notification processing failed for event {}", event.getId());
                outbox.markFailed(event.getId(), "PROCESSING_FAILED");
            }
        }
    }
}
