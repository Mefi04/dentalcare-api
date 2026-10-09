package com.dentalcare.api.modules.clinicalrecords.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.PayloadTooLargeException;
import com.dentalcare.api.exception.UnsupportedMediaTypeException;
import com.dentalcare.api.exception.UnprocessableEntityException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.text.Normalizer;
import java.util.Locale;
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
    private static final int MAX_FILE_NAME_LENGTH = 180;

    private final long maxFileSizeBytes;
    private final String maxFileSizeConfig;

    public ClinicalDocumentFileValidator(
            @Value("${dentalcare.clinical-documents.max-file-size:10MB}") String maxFileSizeConfig) {
        this.maxFileSizeConfig = maxFileSizeConfig;
        this.maxFileSizeBytes = DataSize.parse(maxFileSizeConfig).toBytes();
    }

    public ValidatedClinicalDocumentFile validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is required and cannot be empty");
        }

        if (file.getSize() <= 0) {
            throw new BadRequestException("File must not be empty");
        }

        if (file.getSize() > maxFileSizeBytes) {
            throw new PayloadTooLargeException(
                    "File size exceeds maximum allowed limit of " + maxFileSizeConfig);
        }

        String originalFilename = normalizeFileName(file.getOriginalFilename());

        String rawContentType = file.getContentType();
        if (rawContentType == null || rawContentType.trim().isEmpty()) {
            throw new UnsupportedMediaTypeException("File content type is required");
        }

        String normalizedContentType = rawContentType.split(";")[0].trim().toLowerCase();
        if (!ALLOWED_MIME_TYPES.contains(normalizedContentType)) {
            throw new UnsupportedMediaTypeException("Unsupported file type");
        }

        String extension = extractExtension(originalFilename);
        Set<String> validExtensions = ALLOWED_EXTENSIONS_BY_MIME.get(normalizedContentType);
        if (validExtensions == null || !validExtensions.contains(extension)) {
            throw new UnsupportedMediaTypeException("File extension does not match content type");
        }

        long actualSize = ClinicalDocumentStructureValidator.validate(
                file, normalizedContentType, maxFileSizeBytes, maxFileSizeConfig);
        if (actualSize != file.getSize()) {
            throw new UnprocessableEntityException("Uploaded file size does not match the received content");
        }
        return new ValidatedClinicalDocumentFile(originalFilename, normalizedContentType, actualSize);
    }

    private String normalizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) throw new BadRequestException("File original name is required");
        int lastSlash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        String cleanName = Normalizer.normalize(fileName.substring(lastSlash + 1), Normalizer.Form.NFKC)
                .replaceAll("[\\p{Cntrl}]", "").trim();
        if (cleanName.isBlank() || cleanName.equals(".") || cleanName.equals(".."))
            throw new BadRequestException("File original name is invalid");
        if (cleanName.length() > MAX_FILE_NAME_LENGTH) {
            String extension = extractExtension(cleanName);
            int suffixLength = extension.isEmpty() ? 0 : extension.length() + 1;
            cleanName = cleanName.substring(0, MAX_FILE_NAME_LENGTH - suffixLength)
                    + (suffixLength == 0 ? "" : "." + extension);
        }
        return cleanName;
    }

    private String extractExtension(String fileName) {
        int lastSlash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        String cleanName = (lastSlash >= 0) ? fileName.substring(lastSlash + 1) : fileName;
        int lastDot = cleanName.lastIndexOf('.');
        if (lastDot < 0 || lastDot == cleanName.length() - 1) {
            return "";
        }
        return cleanName.substring(lastDot + 1).toLowerCase(Locale.ROOT).trim();
    }
}
