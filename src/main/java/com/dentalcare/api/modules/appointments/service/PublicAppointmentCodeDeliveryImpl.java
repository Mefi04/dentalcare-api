package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.exception.ServiceUnavailableException;
import com.dentalcare.api.modules.appointments.dto.request.PublicVerificationChannelRequest.Channel;
import com.dentalcare.api.modules.auth.config.PasswordRecoveryProperties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Component
public class PublicAppointmentCodeDeliveryImpl implements PublicAppointmentCodeDelivery {
    private final JavaMailSender mailSender;
    private final PasswordRecoveryProperties mailProperties;
    private final PublicAppointmentVerificationProperties properties;
    private final HttpClient http = HttpClient.newHttpClient();

    public PublicAppointmentCodeDeliveryImpl(JavaMailSender mailSender, PasswordRecoveryProperties mailProperties,
                                             PublicAppointmentVerificationProperties properties) {
        this.mailSender = mailSender; this.mailProperties = mailProperties; this.properties = properties;
    }

    @Override
    public void deliver(Channel channel, String destination, String code, Duration validity) {
        try {
            if (channel == Channel.EMAIL) {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(mailProperties.mailFrom()); message.setTo(destination);
                message.setSubject("Código para tu solicitud de cita DentalCare");
                message.setText("Tu código de verificación es " + code + ". Expira en "
                        + validity.toMinutes() + " minutos. Si no lo solicitaste, ignora este mensaje.");
                mailSender.send(message);
                return;
            }
            if (properties.twilioAccountSid().isBlank() || properties.twilioAuthToken().isBlank()
                    || properties.twilioFromNumber().isBlank()) {
                throw new ServiceUnavailableException("SMS verification is not configured");
            }
            String form = "To=" + enc(phoneForSms(destination)) + "&From=" + enc(properties.twilioFromNumber())
                    + "&Body=" + enc("DentalCare: código " + code + ", válido por " + validity.toMinutes() + " minutos.");
            String auth = Base64.getEncoder().encodeToString((properties.twilioAccountSid() + ":"
                    + properties.twilioAuthToken()).getBytes(StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.twilio.com/2010-04-01/Accounts/"
                    + properties.twilioAccountSid() + "/Messages.json"))
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build();
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new ServiceUnavailableException("SMS verification delivery failed");
        } catch (ServiceUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServiceUnavailableException("Verification code delivery is temporarily unavailable");
        }
    }

    private String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    private String phoneForSms(String value) {
        String normalized = value.trim().replaceAll("[() .-]", "");
        if (normalized.startsWith("+")) return normalized;
        return normalized.startsWith("502") ? "+" + normalized : "+502" + normalized;
    }
}
