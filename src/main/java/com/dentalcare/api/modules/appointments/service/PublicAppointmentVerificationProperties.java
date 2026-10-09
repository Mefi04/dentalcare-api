package com.dentalcare.api.modules.appointments.service;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dentalcare.public-appointment-verification")
public record PublicAppointmentVerificationProperties(String twilioAccountSid, String twilioAuthToken,
                                                       String twilioFromNumber, boolean emailEnabled) {
    public PublicAppointmentVerificationProperties {
        twilioAccountSid = value(twilioAccountSid); twilioAuthToken = value(twilioAuthToken);
        twilioFromNumber = value(twilioFromNumber);
    }
    private static String value(String value) { return value == null ? "" : value; }
}
