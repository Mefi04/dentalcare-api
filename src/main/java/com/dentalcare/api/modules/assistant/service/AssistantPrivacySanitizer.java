package com.dentalcare.api.modules.assistant.service;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AssistantPrivacySanitizer {

    private static final Pattern CUI_PATTERN = Pattern.compile("\\b\\d{4}[\\s-]?\\d{5}[\\s-]?\\d{4}\\b|\\b\\d{13}\\b");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\b(?:\\+?502[\\s.-]?)?[2-7]\\d{3}[\\s.-]?\\d{4}\\b|\\b\\d{8}\\b");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern JWT_PATTERN = Pattern.compile("eyJ[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+");
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile("\\b(?:\\d[ -]?){13,16}\\b");

    public record SanitizationResult(String sanitizedText, boolean piiDetected) {}

    public SanitizationResult sanitize(String input) {
        if (input == null || input.isBlank()) {
            return new SanitizationResult("", false);
        }

        String text = input;
        boolean piiDetected = false;

        if (JWT_PATTERN.matcher(text).find()) {
            text = JWT_PATTERN.matcher(text).replaceAll("[CREDENTIAL_REDACTED]");
            piiDetected = true;
        }

        if (EMAIL_PATTERN.matcher(text).find()) {
            text = EMAIL_PATTERN.matcher(text).replaceAll("[EMAIL_REDACTED]");
            piiDetected = true;
        }

        if (CUI_PATTERN.matcher(text).find()) {
            text = CUI_PATTERN.matcher(text).replaceAll("[DPI_REDACTED]");
            piiDetected = true;
        }

        if (PHONE_PATTERN.matcher(text).find()) {
            text = PHONE_PATTERN.matcher(text).replaceAll("[PHONE_REDACTED]");
            piiDetected = true;
        }

        if (CREDIT_CARD_PATTERN.matcher(text).find()) {
            text = CREDIT_CARD_PATTERN.matcher(text).replaceAll("[CARD_REDACTED]");
            piiDetected = true;
        }

        return new SanitizationResult(text, piiDetected);
    }
}
