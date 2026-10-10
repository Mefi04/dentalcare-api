package com.dentalcare.api.modules.assistant.service;

import com.dentalcare.api.exception.ServiceUnavailableException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiHttpGatewayTests {
    @Test
    void missingKeyFailsWithoutCallingProvider() {
        var gateway = new GeminiHttpGateway("", "gemini-3.8-flash");
        assertThatThrownBy(() -> gateway.generate("rules", "question"))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void modelIdentifierCannotChangeProviderPath() {
        assertThatThrownBy(() -> new GeminiHttpGateway("key", "../../other"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
