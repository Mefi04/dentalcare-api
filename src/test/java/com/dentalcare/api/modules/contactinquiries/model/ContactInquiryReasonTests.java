package com.dentalcare.api.modules.contactinquiries.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContactInquiryReasonTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @ParameterizedTest
    @EnumSource(ContactInquiryReason.class)
    @DisplayName("serializes enum to canonical uppercase string")
    void serializesToCanonicalUppercase(ContactInquiryReason reason) throws Exception {
        String json = objectMapper.writeValueAsString(reason);
        assertThat(json).isEqualTo("\"" + reason.name() + "\"");
    }

    @ParameterizedTest
    @CsvSource({
            "GENERAL, GENERAL",
            "SERVICES, SERVICES",
            "PROFESSIONALS, PROFESSIONALS",
            "LOCATIONS, LOCATIONS",
            "APPOINTMENT_HELP, APPOINTMENT_HELP",
            "ACCOUNT_ACTIVATION, ACCOUNT_ACTIVATION",
            "OTHER, OTHER",
            "general, GENERAL",
            "servicios, SERVICES",
            "odontologos, PROFESSIONALS",
            "sucursales, LOCATIONS",
            "cita, APPOINTMENT_HELP",
            "activar, ACCOUNT_ACTIVATION",
            "otro, OTHER",
            "' GENERAL ', GENERAL",
            "' servicios ', SERVICES"
    })
    @DisplayName("deserializes canonical uppercase and frontend mapping keys cleanly")
    void deserializesCanonicalAndFrontendKeys(String input, ContactInquiryReason expected) throws Exception {
        ContactInquiryReason deserialized = objectMapper.readValue("\"" + input + "\"", ContactInquiryReason.class);
        assertThat(deserialized).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "INVALID_REASON",
            "PRICING",
            "pricing",
            "UNKNOWN",
            "citas",
            "123"
    })
    @DisplayName("fails deserialization for nonexistent or discontinued reasons")
    void failsDeserializationForInvalidReasons(String invalid) {
        assertThatThrownBy(() -> objectMapper.readValue("\"" + invalid + "\"", ContactInquiryReason.class))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("returns null when deserializing null string")
    void deserializesNull() {
        assertThat(ContactInquiryReason.fromValue(null)).isNull();
    }
}
