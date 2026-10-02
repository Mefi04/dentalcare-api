package com.dentalcare.api.modules.clinicalrecords.model;

import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.users.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ClinicalDocumentTests {

    private final Patient patient = mock(Patient.class);
    private final User author = mock(User.class);
    private final Instant now = Instant.parse("2026-10-02T12:00:00Z");

    @Test
    @DisplayName("Legacy constructor initializes R2 file metadata to null and hasFile is false")
    void legacyConstructorInitializesR2MetadataToNull() {
        ClinicalDocument doc = new ClinicalDocument(
                UUID.randomUUID(),
                patient,
                author,
                "Reporte Clínico",
                ClinicalDocumentType.CLINICAL_REPORT,
                "Descripción",
                LocalDate.of(2026, 10, 2),
                now,
                now
        );

        assertThat(doc.getStorageObjectKey()).isNull();
        assertThat(doc.getFileName()).isNull();
        assertThat(doc.getFileSize()).isNull();
        assertThat(doc.getContentType()).isNull();
        assertThat(doc.hasFile()).isFalse();
    }

    @Test
    @DisplayName("Full constructor populates R2 file metadata and hasFile is true")
    void fullConstructorPopulatesR2Metadata() {
        ClinicalDocument doc = new ClinicalDocument(
                UUID.randomUUID(),
                patient,
                author,
                "Radiografía",
                ClinicalDocumentType.RADIOGRAPHY,
                null,
                LocalDate.of(2026, 10, 2),
                now,
                now,
                "patients/p-1/documents/d-1.pdf",
                "radiografia.pdf",
                1024L,
                "application/pdf"
        );

        assertThat(doc.getStorageObjectKey()).isEqualTo("patients/p-1/documents/d-1.pdf");
        assertThat(doc.getFileName()).isEqualTo("radiografia.pdf");
        assertThat(doc.getFileSize()).isEqualTo(1024L);
        assertThat(doc.getContentType()).isEqualTo("application/pdf");
        assertThat(doc.hasFile()).isTrue();
    }

    @Test
    @DisplayName("hasFile returns false when storageObjectKey is null, empty or blank")
    void hasFileReturnsFalseForEmptyOrBlankKey() {
        ClinicalDocument doc = new ClinicalDocument();

        doc.setStorageObjectKey(null);
        assertThat(doc.hasFile()).isFalse();

        doc.setStorageObjectKey("");
        assertThat(doc.hasFile()).isFalse();

        doc.setStorageObjectKey("   ");
        assertThat(doc.hasFile()).isFalse();

        doc.setStorageObjectKey("valid-key");
        assertThat(doc.hasFile()).isTrue();
    }

    @Test
    @DisplayName("setFileSize rejects negative file size")
    void setFileSizeRejectsNegative() {
        ClinicalDocument doc = new ClinicalDocument();

        assertThatThrownBy(() -> doc.setFileSize(-1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El tamaño del archivo no puede ser negativo");

        doc.setFileSize(0L);
        assertThat(doc.getFileSize()).isEqualTo(0L);

        doc.setFileSize(2048L);
        assertThat(doc.getFileSize()).isEqualTo(2048L);

        doc.setFileSize(null);
        assertThat(doc.getFileSize()).isNull();
    }

    @Test
    @DisplayName("Full constructor rejects negative file size")
    void fullConstructorRejectsNegativeFileSize() {
        assertThatThrownBy(() -> new ClinicalDocument(
                UUID.randomUUID(),
                patient,
                author,
                "Doc",
                ClinicalDocumentType.OTHER,
                null,
                LocalDate.of(2026, 10, 2),
                now,
                now,
                "key",
                "file.pdf",
                -50L,
                "application/pdf"
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessage("El tamaño del archivo no puede ser negativo");
    }
}
