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
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public PublicAppointmentCodeDeliveryImpl(JavaMailSender mailSender, PasswordRecoveryProperties mailProperties,
                                             PublicAppointmentVerificationProperties properties) {
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
        this.properties = properties;
    }

    @Override
    public void deliver(Channel channel, String destination, String code, Duration validity) {
        send(channel, destination, "Código para tu solicitud de cita DentalCare",
                "Tu código de verificación es " + code + ". Expira en " + validity.toMinutes()
                        + " minutos. Si no lo solicitaste, ignora este mensaje.");
    }

    @Override
    public void deliverNotice(Channel channel, String destination, String text) {
        send(channel, destination, "Actualización de tu solicitud de cita DentalCare", text);
    }

    @Override
    public boolean isConfigured(Channel channel) {
        if (channel == Channel.EMAIL) return properties.emailEnabled() && !mailProperties.mailFrom().isBlank();
        return !properties.twilioAccountSid().isBlank() && !properties.twilioAuthToken().isBlank()
                && !properties.twilioFromNumber().isBlank();
    }

    private void send(Channel channel, String destination, String subject, String text) {
        try {
            if (!isConfigured(channel)) throw new ServiceUnavailableException("Appointment notification channel unavailable");
            if (channel == Channel.EMAIL) {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(mailProperties.mailFrom());
                message.setTo(destination);
                message.setSubject(subject);
                message.setText(text);
                mailSender.send(message);
                return;
            }
            String form = "To=" + enc(phoneForSms(destination)) + "&From=" + enc(properties.twilioFromNumber())
                    + "&Body=" + enc("DentalCare: " + text);
            String auth = Base64.getEncoder().encodeToString((properties.twilioAccountSid() + ":"
                    + properties.twilioAuthToken()).getBytes(StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.twilio.com/2010-04-01/Accounts/"
                            + properties.twilioAccountSid() + "/Messages.json"))
                    .timeout(Duration.ofSeconds(8))
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build();
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new ServiceUnavailableException("Appointment notification provider rejected delivery");
        } catch (ServiceUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServiceUnavailableException("Appointment notification delivery is temporarily unavailable");
        }
    }

    private String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    private String phoneForSms(String value) {
        String normalized = value.trim().replaceAll("[() .-]", "");
        if (normalized.startsWith("+")) return normalized;
        return normalized.startsWith("502") ? "+" + normalized : "+502" + normalized;
    }
}
