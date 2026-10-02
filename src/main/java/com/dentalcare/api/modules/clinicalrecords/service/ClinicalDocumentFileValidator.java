package com.dentalcare.api.modules.clinicalrecords.service;

import com.dentalcare.api.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;

@Component
public class ClinicalDocumentFileValidator {

    public static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png"
    );

    private static final Map<String, Set<String>> ALLOWED_EXTENSIONS_BY_MIME = Map.of(
            "application/pdf", Set.of("pdf"),
            "image/jpeg", Set.of("jpg", "jpeg"),
            "image/png", Set.of("png")
    );

    private final long maxFileSizeBytes;
    private final String maxFileSizeConfig;

    public ClinicalDocumentFileValidator(
            @Value("${dentalcare.clinical-documents.max-file-size:10MB}") String maxFileSizeConfig) {
        this.maxFileSizeConfig = maxFileSizeConfig;
        this.maxFileSizeBytes = DataSize.parse(maxFileSizeConfig).toBytes();
    }

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is required and cannot be empty");
        }

        if (file.getSize() <= 0) {
            throw new BadRequestException("File must not be empty");
        }

        if (file.getSize() > maxFileSizeBytes) {
            throw new BadRequestException(
                    "File size exceeds maximum allowed limit of " + maxFileSizeConfig);
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            throw new BadRequestException("File original name is required");
        }

        String rawContentType = file.getContentType();
        if (rawContentType == null || rawContentType.trim().isEmpty()) {
            throw new BadRequestException("File content type is required");
        }

        String normalizedContentType = rawContentType.split(";")[0].trim().toLowerCase();
        if (!ALLOWED_MIME_TYPES.contains(normalizedContentType)) {
            throw new BadRequestException(
                    "Unsupported file type: " + rawContentType + ". Allowed types: application/pdf, image/jpeg, image/png");
        }

        String extension = extractExtension(originalFilename);
        Set<String> validExtensions = ALLOWED_EXTENSIONS_BY_MIME.get(normalizedContentType);
        if (validExtensions == null || !validExtensions.contains(extension)) {
            throw new BadRequestException(
                    "File extension '." + extension + "' does not match content type '" + rawContentType + "'");
        }

        validateMagicBytes(file, normalizedContentType);
    }

    private void validateMagicBytes(MultipartFile file, String normalizedContentType) {
        byte[] header = new byte[8];
        int bytesRead;
        try (InputStream is = file.getInputStream()) {
            bytesRead = is.read(header);
        } catch (IOException e) {
            throw new BadRequestException("Failed to inspect file content");
        }

        if (bytesRead < 2) {
            throw new BadRequestException("File content is too short to determine file type");
        }

        switch (normalizedContentType) {
            case "application/pdf" -> {
                // PDF magic bytes: %PDF (0x25, 0x50, 0x44, 0x46)
                if (bytesRead < 4
                        || header[0] != 0x25
                        || header[1] != 0x50
                        || header[2] != 0x44
                        || header[3] != 0x46) {
                    throw new BadRequestException("File content does not match PDF format");
                }
            }
            case "image/jpeg" -> {
                // JPEG magic bytes: 0xFF, 0xD8
                if ((header[0] & 0xFF) != 0xFF || (header[1] & 0xFF) != 0xD8) {
                    throw new BadRequestException("File content does not match JPEG format");
                }
            }
            case "image/png" -> {
                // PNG magic bytes: 0x89, 0x50, 0x4E, 0x47
                if (bytesRead < 4
                        || (header[0] & 0xFF) != 0x89
                        || header[1] != 0x50
                        || header[2] != 0x4E
                        || header[3] != 0x47) {
                    throw new BadRequestException("File content does not match PNG format");
                }
            }
            default -> throw new BadRequestException("Unsupported content type for file validation");
        }
    }

    private String extractExtension(String fileName) {
        int lastSlash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        String cleanName = (lastSlash >= 0) ? fileName.substring(lastSlash + 1) : fileName;
        int lastDot = cleanName.lastIndexOf('.');
        if (lastDot < 0 || lastDot == cleanName.length() - 1) {
            return "";
        }
        return cleanName.substring(lastDot + 1).toLowerCase().trim();
    }
}
