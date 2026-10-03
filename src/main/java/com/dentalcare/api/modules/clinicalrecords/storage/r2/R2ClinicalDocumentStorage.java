package com.dentalcare.api.modules.clinicalrecords.storage.r2;

import com.dentalcare.api.config.r2.R2Properties;
import com.dentalcare.api.modules.clinicalrecords.storage.ClinicalDocumentStorage;
import com.dentalcare.api.modules.clinicalrecords.storage.StoredDocument;
import com.dentalcare.api.modules.clinicalrecords.storage.StoredDocumentContent;
import com.dentalcare.api.modules.clinicalrecords.storage.StoredDocumentMetadata;
import com.dentalcare.api.modules.clinicalrecords.storage.UploadDocumentCommand;
import com.dentalcare.api.modules.clinicalrecords.storage.exception.DocumentNotFoundInStorageException;
import com.dentalcare.api.modules.clinicalrecords.storage.exception.DocumentStorageException;
import com.dentalcare.api.modules.clinicalrecords.storage.exception.DocumentStorageUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(prefix = "dentalcare.r2", name = "enabled", havingValue = "true")
public class R2ClinicalDocumentStorage implements ClinicalDocumentStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger(R2ClinicalDocumentStorage.class);
    private static final Pattern SAFE_EXTENSION_PATTERN = Pattern.compile("^[a-z0-9]{1,10}$");

    private final S3Client s3Client;
    private final R2Properties properties;

    public R2ClinicalDocumentStorage(S3Client s3Client, R2Properties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    @Override
    public StoredDocument store(UploadDocumentCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Upload command is required");
        }

        String objectKey = generateObjectKey(command.patientId(), command.originalFileName());
        String sanitizedName = sanitizeFileName(command.originalFileName());

        try {
            byte[] bytes = command.inputStream().readAllBytes();
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .contentType(command.contentType())
                    .contentLength((long) bytes.length)
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(bytes));

            return new StoredDocument(
                    objectKey,
                    sanitizedName,
                    (long) bytes.length,
                    command.contentType()
            );
        } catch (IOException exception) {
            LOGGER.error("Failed to read document stream: {}", exception.getMessage(), exception);
            throw new DocumentStorageException("Failed to read document content", exception);
        } catch (S3Exception exception) {
            LOGGER.error("Failed to store document in R2 storage: status={}", exception.statusCode(), exception);
            throw translateAwsException("Failed to store document in storage", exception);
        } catch (SdkClientException exception) {
            LOGGER.error("R2 storage client error during upload: {}", exception.getMessage(), exception);
            throw new DocumentStorageUnavailableException("Storage service is currently unavailable", exception);
        } catch (Exception exception) {
            LOGGER.error("Unexpected error during document upload to storage: {}", exception.getMessage(), exception);
            throw new DocumentStorageException("Failed to store document in storage", exception);
        }
    }

    @Override
    public StoredDocumentContent load(String storageObjectKey) {
        validateStorageObjectKey(storageObjectKey);

        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(storageObjectKey)
                    .build();

            ResponseInputStream<GetObjectResponse> responseStream = s3Client.getObject(getRequest);
            GetObjectResponse response = responseStream.response();

            String contentType = response.contentType() != null ? response.contentType() : "application/octet-stream";
            long contentLength = response.contentLength() != null ? response.contentLength() : 0L;

            return new StoredDocumentContent(
                    storageObjectKey,
                    contentType,
                    contentLength,
                    responseStream
            );
        } catch (NoSuchKeyException exception) {
            throw new DocumentNotFoundInStorageException("Document not found in storage", exception);
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new DocumentNotFoundInStorageException("Document not found in storage", exception);
            }
            LOGGER.error("Failed to retrieve document from R2 storage: status={}", exception.statusCode(), exception);
            throw translateAwsException("Failed to retrieve document from storage", exception);
        } catch (SdkClientException exception) {
            LOGGER.error("R2 storage client error during download: {}", exception.getMessage(), exception);
            throw new DocumentStorageUnavailableException("Storage service is currently unavailable", exception);
        } catch (Exception exception) {
            LOGGER.error("Unexpected error retrieving document from storage: {}", exception.getMessage(), exception);
            throw new DocumentStorageException("Failed to retrieve document from storage", exception);
        }
    }

    @Override
    public StoredDocumentMetadata getMetadata(String storageObjectKey) {
        validateStorageObjectKey(storageObjectKey);

        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(storageObjectKey)
                    .build();

            HeadObjectResponse response = s3Client.headObject(headRequest);
            long contentLength = response.contentLength() != null ? response.contentLength() : 0L;
            String contentType = response.contentType() != null ? response.contentType() : "application/octet-stream";
            Instant lastModified = response.lastModified() != null ? response.lastModified() : Instant.now();

            return new StoredDocumentMetadata(
                    storageObjectKey,
                    contentLength,
                    contentType,
                    lastModified
            );
        } catch (NoSuchKeyException exception) {
            throw new DocumentNotFoundInStorageException("Document not found in storage", exception);
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new DocumentNotFoundInStorageException("Document not found in storage", exception);
            }
            LOGGER.error("Failed to check metadata in R2 storage: status={}", exception.statusCode(), exception);
            throw translateAwsException("Failed to retrieve document metadata from storage", exception);
        } catch (SdkClientException exception) {
            LOGGER.error("R2 storage client error during headObject: {}", exception.getMessage(), exception);
            throw new DocumentStorageUnavailableException("Storage service is currently unavailable", exception);
        } catch (Exception exception) {
            LOGGER.error("Unexpected error checking document metadata in storage: {}", exception.getMessage(), exception);
            throw new DocumentStorageException("Failed to check document metadata in storage", exception);
        }
    }

    @Override
    public boolean exists(String storageObjectKey) {
        if (storageObjectKey == null || storageObjectKey.trim().isEmpty()) {
            return false;
        }

        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(storageObjectKey)
                    .build();

            s3Client.headObject(headRequest);
            return true;
        } catch (NoSuchKeyException exception) {
            return false;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            LOGGER.error("Failed to check existence in R2 storage: status={}", exception.statusCode(), exception);
            throw translateAwsException("Failed to check document existence in storage", exception);
        } catch (SdkClientException exception) {
            LOGGER.error("R2 storage client error during exists check: {}", exception.getMessage(), exception);
            throw new DocumentStorageUnavailableException("Storage service is currently unavailable", exception);
        } catch (Exception exception) {
            LOGGER.error("Unexpected error checking document existence in storage: {}", exception.getMessage(), exception);
            throw new DocumentStorageException("Failed to check document existence in storage", exception);
        }
    }

    @Override
    public void delete(String storageObjectKey) {
        validateStorageObjectKey(storageObjectKey);

        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(storageObjectKey)
                    .build();

            s3Client.deleteObject(deleteRequest);
        } catch (NoSuchKeyException exception) {
            LOGGER.debug("Object already absent from storage during delete: {}", storageObjectKey);
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return;
            }
            LOGGER.error("Failed to delete document from R2 storage: status={}", exception.statusCode(), exception);
            throw translateAwsException("Failed to delete document from storage", exception);
        } catch (SdkClientException exception) {
            LOGGER.error("R2 storage client error during delete: {}", exception.getMessage(), exception);
            throw new DocumentStorageUnavailableException("Storage service is currently unavailable", exception);
        } catch (Exception exception) {
            LOGGER.error("Unexpected error deleting document from storage: {}", exception.getMessage(), exception);
            throw new DocumentStorageException("Failed to delete document from storage", exception);
        }
    }

    public String generateObjectKey(UUID patientId, String originalFileName) {
        if (patientId == null) {
            throw new IllegalArgumentException("Patient ID is required");
        }
        UUID documentUuid = UUID.randomUUID();
        String extension = extractSafeExtension(originalFileName);
        if (extension.isEmpty()) {
            return "patients/" + patientId + "/documents/" + documentUuid;
        }
        return "patients/" + patientId + "/documents/" + documentUuid + "." + extension;
    }

    private String extractSafeExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int lastSlash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        String cleanName = (lastSlash >= 0) ? fileName.substring(lastSlash + 1) : fileName;
        int lastDot = cleanName.lastIndexOf('.');
        if (lastDot < 0 || lastDot == cleanName.length() - 1) {
            return "";
        }
        String ext = cleanName.substring(lastDot + 1).toLowerCase().trim();
        if (SAFE_EXTENSION_PATTERN.matcher(ext).matches()) {
            return ext;
        }
        return "";
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return "document";
        }
        int lastSlash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        String cleanName = (lastSlash >= 0) ? fileName.substring(lastSlash + 1) : fileName;
        cleanName = cleanName.trim();
        return cleanName.isEmpty() ? "document" : cleanName;
    }

    private void validateStorageObjectKey(String storageObjectKey) {
        if (storageObjectKey == null || storageObjectKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Storage object key is required");
        }
    }

    private DocumentStorageException translateAwsException(String defaultMessage, S3Exception exception) {
        if (exception.statusCode() == 404) {
            return new DocumentNotFoundInStorageException("Document not found in storage", exception);
        }
        if (exception.statusCode() == 403) {
            return new DocumentStorageException("Access denied to storage service", exception);
        }
        if (exception.statusCode() == 503 || exception.statusCode() == 500) {
            return new DocumentStorageUnavailableException("Storage service is currently unavailable", exception);
        }
        return new DocumentStorageException(defaultMessage, exception);
    }
}
