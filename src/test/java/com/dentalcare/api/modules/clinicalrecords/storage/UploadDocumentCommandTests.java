package com.dentalcare.api.modules.clinicalrecords.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadDocumentCommandTests {

    private final UUID patientId = UUID.randomUUID();
    private final InputStream stream = new ByteArrayInputStream("test".getBytes());

    @Test
    @DisplayName("Valid upload command constructs properly")
    void validUploadCommandConstructsProperly() {
        UploadDocumentCommand command = new UploadDocumentCommand(
                patientId,
                "radiografia.pdf",
                "application/pdf",
                1024L,
                stream
        );

        assertThat(command.patientId()).isEqualTo(patientId);
        assertThat(command.originalFileName()).isEqualTo("radiografia.pdf");
        assertThat(command.contentType()).isEqualTo("application/pdf");
        assertThat(command.contentLength()).isEqualTo(1024L);
        assertThat(command.inputStream()).isNotNull();
    }

    @Test
    @DisplayName("Rejects null patientId")
    void rejectsNullPatientId() {
        assertThatThrownBy(() -> new UploadDocumentCommand(null, "doc.pdf", "application/pdf", 10L, stream))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Patient ID is required");
    }

    @Test
    @DisplayName("Rejects blank originalFileName")
    void rejectsBlankOriginalFileName() {
        assertThatThrownBy(() -> new UploadDocumentCommand(patientId, "  ", "application/pdf", 10L, stream))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Original file name is required");

        assertThatThrownBy(() -> new UploadDocumentCommand(patientId, null, "application/pdf", 10L, stream))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Original file name is required");
    }

    @Test
    @DisplayName("Rejects blank contentType")
    void rejectsBlankContentType() {
        assertThatThrownBy(() -> new UploadDocumentCommand(patientId, "doc.pdf", "  ", 10L, stream))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Content type is required");
    }

    @Test
    @DisplayName("Rejects negative contentLength")
    void rejectsNegativeContentLength() {
        assertThatThrownBy(() -> new UploadDocumentCommand(patientId, "doc.pdf", "application/pdf", -1L, stream))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Content length must be non-negative");
    }

    @Test
    @DisplayName("Rejects null inputStream")
    void rejectsNullInputStream() {
        assertThatThrownBy(() -> new UploadDocumentCommand(patientId, "doc.pdf", "application/pdf", 10L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Input stream is required");
    }
}
