package com.dentalcare.api.modules.clinicalrecords.mapper;

import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalDocumentResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalProfessionalResponse;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDocument;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDocumentType;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.users.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClinicalDocumentMapperTests {

    private ClinicalDocumentMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ClinicalDocumentMapper();
    }

    @Test
    @DisplayName("toResponse returns null when document is null")
    void toResponseReturnsNullWhenDocumentIsNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }

    @Test
    @DisplayName("toProfessionalResponse returns null when user is null")
    void toProfessionalResponseReturnsNullWhenUserIsNull() {
        assertThat(mapper.toProfessionalResponse(null)).isNull();
    }

    @Test
    @DisplayName("toResponse maps legacy document properly without file metadata")
    void toResponseMapsAllFieldsProperly() {
        UUID docId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();

        Patient patient = mock(Patient.class);
        when(patient.getId()).thenReturn(patientId);

        User author = mock(User.class);
        when(author.getId()).thenReturn(authorId);
        when(author.getFullName()).thenReturn("Dra. Ana López");

        LocalDate docDate = LocalDate.of(2026, 10, 1);
        Instant now = Instant.parse("2026-10-01T15:00:00Z");

        ClinicalDocument document = new ClinicalDocument(
                docId,
                patient,
                author,
                "Radiografía Panorámica",
                ClinicalDocumentType.RADIOGRAPHY,
                "Control post-operatorio",
                docDate,
                now,
                now
        );

        ClinicalDocumentResponse response = mapper.toResponse(document);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(docId);
        assertThat(response.patientId()).isEqualTo(patientId);
        assertThat(response.title()).isEqualTo("Radiografía Panorámica");
        assertThat(response.type()).isEqualTo(ClinicalDocumentType.RADIOGRAPHY);
        assertThat(response.description()).isEqualTo("Control post-operatorio");
        assertThat(response.documentDate()).isEqualTo(docDate);
        assertThat(response.createdAt()).isEqualTo(now);
        assertThat(response.updatedAt()).isEqualTo(now);

        // R2 metadata assertions for legacy document
        assertThat(response.hasFile()).isFalse();
        assertThat(response.fileName()).isNull();
        assertThat(response.fileSize()).isNull();
        assertThat(response.contentType()).isNull();

        ClinicalProfessionalResponse authorResp = response.author();
        assertThat(authorResp).isNotNull();
        assertThat(authorResp.id()).isEqualTo(authorId);
        assertThat(authorResp.fullName()).isEqualTo("Dra. Ana López");
    }

    @Test
    @DisplayName("toResponse maps document with R2 file metadata properly")
    void toResponseMapsDocumentWithR2MetadataProperly() {
        UUID docId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();

        Patient patient = mock(Patient.class);
        when(patient.getId()).thenReturn(patientId);

        User author = mock(User.class);
        when(author.getId()).thenReturn(authorId);
        when(author.getFullName()).thenReturn("Dr. Carlos Pérez");

        LocalDate docDate = LocalDate.of(2026, 10, 2);
        Instant now = Instant.parse("2026-10-02T16:00:00Z");

        ClinicalDocument document = new ClinicalDocument(
                docId,
                patient,
                author,
                "Radiografía Periapical",
                ClinicalDocumentType.RADIOGRAPHY,
                "Pieza 18",
                docDate,
                now,
                now,
                "patients/" + patientId + "/documents/" + docId + ".pdf",
                "radiografia-pieza-18.pdf",
                245672L,
                "application/pdf"
        );

        ClinicalDocumentResponse response = mapper.toResponse(document);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(docId);
        assertThat(response.patientId()).isEqualTo(patientId);
        assertThat(response.title()).isEqualTo("Radiografía Periapical");
        assertThat(response.type()).isEqualTo(ClinicalDocumentType.RADIOGRAPHY);
        assertThat(response.description()).isEqualTo("Pieza 18");
        assertThat(response.documentDate()).isEqualTo(docDate);
        assertThat(response.createdAt()).isEqualTo(now);
        assertThat(response.updatedAt()).isEqualTo(now);

        // R2 metadata assertions
        assertThat(response.hasFile()).isTrue();
        assertThat(response.fileName()).isEqualTo("radiografia-pieza-18.pdf");
        assertThat(response.fileSize()).isEqualTo(245672L);
        assertThat(response.contentType()).isEqualTo("application/pdf");
    }
}
