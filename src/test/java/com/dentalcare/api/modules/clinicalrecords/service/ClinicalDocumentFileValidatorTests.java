package com.dentalcare.api.modules.clinicalrecords.service;

import com.dentalcare.api.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClinicalDocumentFileValidatorTests {

    private ClinicalDocumentFileValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ClinicalDocumentFileValidator("10MB");
    }

    @Test
    @DisplayName("Valid PDF file with %PDF magic bytes passes validation")
    void validPdfPassesValidation() {
        byte[] pdfBytes = "%PDF-1.4 test content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "radiography.pdf", "application/pdf", pdfBytes);

        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Valid JPEG file with 0xFF 0xD8 magic bytes passes validation")
    void validJpegPassesValidation() {
        byte[] jpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", jpegBytes);

        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Valid PNG file with 0x89 PNG magic bytes passes validation")
    void validPngPassesValidation() {
        byte[] pngBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        MockMultipartFile file = new MockMultipartFile(
                "file", "capture.png", "image/png", pngBytes);

        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Null file throws BadRequestException")
    void nullFileThrowsBadRequest() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("File is required and cannot be empty");
    }

    @Test
    @DisplayName("Empty file (0 bytes) throws BadRequestException")
    void emptyFileThrowsBadRequest() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cannot be empty");
    }

    @Test
    @DisplayName("File exceeding max size limit throws BadRequestException")
    void fileExceedingMaxSizeThrowsBadRequest() {
        ClinicalDocumentFileValidator smallLimitValidator = new ClinicalDocumentFileValidator("1KB");
        byte[] largeBytes = new byte[2048];
        largeBytes[0] = 0x25; largeBytes[1] = 0x50; largeBytes[2] = 0x44; largeBytes[3] = 0x46; // %PDF

        MockMultipartFile file = new MockMultipartFile(
                "file", "large.pdf", "application/pdf", largeBytes);

        assertThatThrownBy(() -> smallLimitValidator.validate(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("File size exceeds maximum allowed limit");
    }

    @Test
    @DisplayName("Blank original file name throws BadRequestException")
    void blankFileNameThrowsBadRequest() {
        byte[] pdfBytes = "%PDF-1.4 test".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "   ", "application/pdf", pdfBytes);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("File original name is required");
    }

    @Test
    @DisplayName("Blank content type throws BadRequestException")
    void blankContentTypeThrowsBadRequest() {
        byte[] pdfBytes = "%PDF-1.4 test".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "   ", pdfBytes);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("File content type is required");
    }

    @Test
    @DisplayName("Disallowed MIME types (executables, html, octet-stream) throw BadRequestException")
    void disallowedMimeTypeThrowsBadRequest() {
        byte[] content = "dummy content".getBytes(StandardCharsets.UTF_8);

        MockMultipartFile exeFile = new MockMultipartFile(
                "file", "file.exe", "application/x-msdownload", content);
        assertThatThrownBy(() -> validator.validate(exeFile))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unsupported file type");

        MockMultipartFile htmlFile = new MockMultipartFile(
                "file", "page.html", "text/html", content);
        assertThatThrownBy(() -> validator.validate(htmlFile))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unsupported file type");

        MockMultipartFile binFile = new MockMultipartFile(
                "file", "file.bin", "application/octet-stream", content);
        assertThatThrownBy(() -> validator.validate(binFile))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unsupported file type");
    }

    @Test
    @DisplayName("Extension mismatch with MIME type throws BadRequestException")
    void extensionMismatchThrowsBadRequest() {
        byte[] pdfBytes = "%PDF-1.4 test".getBytes(StandardCharsets.UTF_8);

        // Claiming application/pdf but with .exe extension
        MockMultipartFile file = new MockMultipartFile(
                "file", "malicious.exe", "application/pdf", pdfBytes);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("does not match content type");
    }

    @Test
    @DisplayName("Mismatched magic bytes (text bytes disguised as PDF) throw BadRequestException")
    void mismatchedMagicBytesThrowsBadRequest() {
        byte[] textBytes = "This is not a real PDF file".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.pdf", "application/pdf", textBytes);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("File content does not match PDF format");
    }
}
