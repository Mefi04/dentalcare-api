package com.dentalcare.api.modules.assistant.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantPrivacySanitizerTests {

    private AssistantPrivacySanitizer sanitizer;

    @BeforeEach
    void setUp() {
        sanitizer = new AssistantPrivacySanitizer();
    }

    @Test
    void cleanMessageLeavesTextIntactWithoutPiiFlag() {
        var result = sanitizer.sanitize("¿Qué horario tienen los sábados y qué servicios ofrecen?");
        assertThat(result.piiDetected()).isFalse();
        assertThat(result.sanitizedText()).isEqualTo("¿Qué horario tienen los sábados y qué servicios ofrecen?");
    }

    @Test
    void detectsAndRedactsGuatemalanDpi() {
        var result = sanitizer.sanitize("Hola mi DPI es 2345678901234 y quiero una cita");
        assertThat(result.piiDetected()).isTrue();
        assertThat(result.sanitizedText()).doesNotContain("2345678901234");
        assertThat(result.sanitizedText()).contains("[DPI_REDACTED]");
    }

    @Test
    void detectsAndRedactsGuatemalanDpiWithHyphens() {
        var result = sanitizer.sanitize("CUI: 2345-67890-1234 por favor");
        assertThat(result.piiDetected()).isTrue();
        assertThat(result.sanitizedText()).doesNotContain("2345-67890-1234");
        assertThat(result.sanitizedText()).contains("[DPI_REDACTED]");
    }

    @Test
    void detectsAndRedactsPhoneNumber() {
        var result = sanitizer.sanitize("Mi teléfono es 55550101 o +502 4444-2222");
        assertThat(result.piiDetected()).isTrue();
        assertThat(result.sanitizedText()).doesNotContain("55550101");
        assertThat(result.sanitizedText()).contains("[PHONE_REDACTED]");
    }

    @Test
    void detectsAndRedactsEmailAddress() {
        var result = sanitizer.sanitize("Escríbanme a paciente@example.com con los precios");
        assertThat(result.piiDetected()).isTrue();
        assertThat(result.sanitizedText()).doesNotContain("paciente@example.com");
        assertThat(result.sanitizedText()).contains("[EMAIL_REDACTED]");
    }

    @Test
    void detectsAndRedactsJwtTokens() {
        String jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.doNotLeakThisSignature";
        var result = sanitizer.sanitize("Tengo este token: " + jwt);
        assertThat(result.piiDetected()).isTrue();
        assertThat(result.sanitizedText()).doesNotContain(jwt);
        assertThat(result.sanitizedText()).contains("[CREDENTIAL_REDACTED]");
    }
}
