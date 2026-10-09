package com.dentalcare.api.modules.clinicalrecords.service;

import com.dentalcare.api.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

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
        byte[] pdfBytes = validPdf();
        MockMultipartFile file = new MockMultipartFile(
                "file", "radiography.pdf", "application/pdf", pdfBytes);

        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Valid JPEG file with 0xFF 0xD8 magic bytes passes validation")
    void validJpegPassesValidation() {
        byte[] jpegBytes = validJpeg();
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", jpegBytes);

        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Valid PNG file with 0x89 PNG magic bytes passes validation")
    void validPngPassesValidation() {
        byte[] pngBytes = validPng();
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

    @Test
    void markerOnlyPdfIsRejected() {
        assertThatThrownBy(() -> validator.validate(file("fake.pdf", "application/pdf",
                "%PDF-1.7\n1 0 obj << /Type /Catalog >> endobj\nstartxref\n9\n%%EOF".getBytes(StandardCharsets.US_ASCII))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void markerOnlyAndTruncatedJpegAreRejected() {
        assertThatThrownBy(() -> validator.validate(file("fake.jpg", "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xd9})))
                .isInstanceOf(BadRequestException.class);
        byte[] truncated = validJpeg();
        assertThatThrownBy(() -> validator.validate(file("truncated.jpg", "image/jpeg",
                java.util.Arrays.copyOf(truncated, truncated.length - 1))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void pngWithoutIhdrAndPngWithBadCrcAreRejected() {
        byte[] markerOnly = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
                0, 0, 0, 0, 'I', 'E', 'N', 'D', (byte) 0xae, 0x42, 0x60, (byte) 0x82};
        assertThatThrownBy(() -> validator.validate(file("fake.png", "image/png", markerOnly)))
                .isInstanceOf(BadRequestException.class);
        byte[] badCrc = validPng();
        badCrc[badCrc.length - 1] ^= 1;
        assertThatThrownBy(() -> validator.validate(file("bad.png", "image/png", badCrc)))
                .isInstanceOf(BadRequestException.class);
    }

    private MockMultipartFile file(String name, String type, byte[] content) {
        return new MockMultipartFile("file", name, type, content);
    }

    private static byte[] validPdf() {
        String objects = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n"
                + "2 0 obj\n<< /Type /Pages /Count 0 /Kids [] >>\nendobj\n";
        int xref = objects.getBytes(StandardCharsets.US_ASCII).length;
        String references = "xref\n0 3\n0000000000 65535 f \n0000000009 00000 n \n0000000062 00000 n \n"
                + "trailer\n<< /Size 3 /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n";
        return (objects + references).getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] validJpeg() {
        return new byte[]{
                (byte) 0xff, (byte) 0xd8,
                (byte) 0xff, (byte) 0xc0, 0, 11, 8, 0, 1, 0, 1, 1, 1, 0x11, 0,
                (byte) 0xff, (byte) 0xda, 0, 8, 1, 1, 0, 0, 63, 0,
                0,
                (byte) 0xff, (byte) 0xd9};
    }

    private static byte[] validPng() {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            output.write(new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
            writeChunk(output, "IHDR", new byte[]{0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0});
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed)) {
                deflater.write(new byte[]{0, 0, 0, 0});
            }
            writeChunk(output, "IDAT", compressed.toByteArray());
            writeChunk(output, "IEND", new byte[0]);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void writeChunk(ByteArrayOutputStream output, String type, byte[] data) throws IOException {
        output.write(new byte[]{(byte) (data.length >>> 24), (byte) (data.length >>> 16),
                (byte) (data.length >>> 8), (byte) data.length});
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        output.write(typeBytes);
        output.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        long value = crc.getValue();
        output.write(new byte[]{(byte) (value >>> 24), (byte) (value >>> 16),
                (byte) (value >>> 8), (byte) value});
    }
}
