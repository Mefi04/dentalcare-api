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

import java.time.Instant;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
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
            if (command.contentLength() <= 0) {
                throw new IllegalArgumentException("Document content length must be positive");
            }
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .contentType(command.contentType())
                    .contentLength(command.contentLength())
                    .build();

            BoundedCountingInputStream transferStream =
                    new BoundedCountingInputStream(command.inputStream(), command.contentLength());
            s3Client.putObject(putRequest,
                    RequestBody.fromInputStream(transferStream, command.contentLength()));
            transferStream.verifyFullyConsumed();

            return new StoredDocument(
                    objectKey,
                    sanitizedName,
                    command.contentLength(),
                    command.contentType()
            );
        } catch (S3Exception exception) {
            bestEffortDeleteAfterFailedUpload(objectKey);
            LOGGER.error("R2 upload failed with status {}", exception.statusCode());
            throw translateAwsException("Failed to store document in storage", exception);
        } catch (SdkClientException exception) {
            bestEffortDeleteAfterFailedUpload(objectKey);
            LOGGER.error("R2 upload client failure: {}", exception.getClass().getSimpleName());
            throw new DocumentStorageUnavailableException("Storage service is currently unavailable", exception);
        } catch (Exception exception) {
            bestEffortDeleteAfterFailedUpload(objectKey);
            LOGGER.error("Unexpected R2 upload failure: {}", exception.getClass().getSimpleName());
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
            LOGGER.error("R2 download failed with status {}", exception.statusCode());
            throw translateAwsException("Failed to retrieve document from storage", exception);
        } catch (SdkClientException exception) {
            LOGGER.error("R2 download client failure: {}", exception.getClass().getSimpleName());
            throw new DocumentStorageUnavailableException("Storage service is currently unavailable", exception);
        } catch (Exception exception) {
            LOGGER.error("Unexpected R2 download failure: {}", exception.getClass().getSimpleName());
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
            LOGGER.error("R2 metadata request failed with status {}", exception.statusCode());
            throw translateAwsException("Failed to retrieve document metadata from storage", exception);
        } catch (SdkClientException exception) {
            LOGGER.error("R2 metadata client failure: {}", exception.getClass().getSimpleName());
            throw new DocumentStorageUnavailableException("Storage service is currently unavailable", exception);
        } catch (Exception exception) {
            LOGGER.error("Unexpected R2 metadata failure: {}", exception.getClass().getSimpleName());
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
            LOGGER.error("R2 existence request failed with status {}", exception.statusCode());
            throw translateAwsException("Failed to check document existence in storage", exception);
        } catch (SdkClientException exception) {
            LOGGER.error("R2 existence client failure: {}", exception.getClass().getSimpleName());
            throw new DocumentStorageUnavailableException("Storage service is currently unavailable", exception);
        } catch (Exception exception) {
            LOGGER.error("Unexpected R2 existence failure: {}", exception.getClass().getSimpleName());
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
            LOGGER.debug("R2 object was already absent during delete");
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return;
            }
            LOGGER.error("R2 delete failed with status {}", exception.statusCode());
            throw translateAwsException("Failed to delete document from storage", exception);
        } catch (SdkClientException exception) {
            LOGGER.error("R2 delete client failure: {}", exception.getClass().getSimpleName());
            throw new DocumentStorageUnavailableException("Storage service is currently unavailable", exception);
        } catch (Exception exception) {
            LOGGER.error("Unexpected R2 delete failure: {}", exception.getClass().getSimpleName());
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

    private void bestEffortDeleteAfterFailedUpload(String objectKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.getBucket()).key(objectKey).build());
        } catch (RuntimeException cleanupFailure) {
            LOGGER.error("Cleanup after an ambiguous R2 upload failure also failed: {}",
                    cleanupFailure.getClass().getSimpleName());
        }
    }

    private static final class BoundedCountingInputStream extends FilterInputStream {
        private final long expectedLength;
        private long consumed;

        private BoundedCountingInputStream(InputStream input, long expectedLength) {
            super(input);
            this.expectedLength = expectedLength;
        }

        @Override
        public int read() throws IOException {
            if (consumed >= expectedLength) return -1;
            int value = super.read();
            if (value >= 0) consumed++;
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (consumed >= expectedLength) return -1;
            int allowed = (int) Math.min(length, expectedLength - consumed);
            int read = super.read(buffer, offset, allowed);
            if (read > 0) consumed += read;
            return read;
        }

        private void verifyFullyConsumed() throws IOException {
            if (consumed != expectedLength) {
                throw new IOException("Upload stream ended before the validated content length");
            }
            if (in.read() != -1) {
                throw new IOException("Upload stream exceeded the validated content length");
            }
        }
    }
}
