package com.dentalcare.api.modules.auth.service;

import com.dentalcare.api.modules.auth.config.PasswordRecoveryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class EmailPasswordRecoveryCodeDelivery implements PasswordRecoveryCodeDelivery {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailPasswordRecoveryCodeDelivery.class);

    private final JavaMailSender mailSender;
    private final PasswordRecoveryProperties properties;

    public EmailPasswordRecoveryCodeDelivery(JavaMailSender mailSender, PasswordRecoveryProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    @Async
    public void deliver(String email, String code, Duration validity) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(email);
        message.setSubject("Código de recuperación de DentalCare");
        message.setText("Tu código de recuperación es " + code + ". Expira en "
                + validity.toMinutes() + " minutos. Si no lo solicitaste, ignora este mensaje.");
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            LOGGER.warn("Password recovery email delivery failed: {}",
                    exception.getClass().getSimpleName());
        }
    }
}
