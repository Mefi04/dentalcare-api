package com.dentalcare.api.modules.clinicalrecords.storage.r2;

import com.dentalcare.api.modules.clinicalrecords.storage.ClinicalDocumentStorage;
import com.dentalcare.api.modules.clinicalrecords.storage.UploadDocumentCommand;
import com.dentalcare.api.modules.clinicalrecords.storage.exception.DocumentStorageDisabledException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DisabledClinicalDocumentStorageTests {

    private final ClinicalDocumentStorage storage = new DisabledClinicalDocumentStorage();

    @Test
    @DisplayName("store throws DocumentStorageDisabledException")
    void storeThrowsWhenDisabled() {
        UploadDocumentCommand command = new UploadDocumentCommand(
                UUID.randomUUID(),
                "file.pdf",
                "application/pdf",
                100L,
                new ByteArrayInputStream("data".getBytes())
        );

        assertThatThrownBy(() -> storage.store(command))
                .isInstanceOf(DocumentStorageDisabledException.class)
                .hasMessage("Cloudflare R2 storage is disabled");
    }

    @Test
    @DisplayName("load throws DocumentStorageDisabledException")
    void loadThrowsWhenDisabled() {
        assertThatThrownBy(() -> storage.load("patients/123/documents/abc.pdf"))
                .isInstanceOf(DocumentStorageDisabledException.class)
                .hasMessage("Cloudflare R2 storage is disabled");
    }

    @Test
    @DisplayName("getMetadata throws DocumentStorageDisabledException")
    void getMetadataThrowsWhenDisabled() {
        assertThatThrownBy(() -> storage.getMetadata("patients/123/documents/abc.pdf"))
                .isInstanceOf(DocumentStorageDisabledException.class)
                .hasMessage("Cloudflare R2 storage is disabled");
    }

    @Test
    @DisplayName("exists throws DocumentStorageDisabledException")
    void existsThrowsWhenDisabled() {
        assertThatThrownBy(() -> storage.exists("patients/123/documents/abc.pdf"))
                .isInstanceOf(DocumentStorageDisabledException.class)
                .hasMessage("Cloudflare R2 storage is disabled");
    }

    @Test
    @DisplayName("delete throws DocumentStorageDisabledException")
    void deleteThrowsWhenDisabled() {
        assertThatThrownBy(() -> storage.delete("patients/123/documents/abc.pdf"))
                .isInstanceOf(DocumentStorageDisabledException.class)
                .hasMessage("Cloudflare R2 storage is disabled");
    }
}
