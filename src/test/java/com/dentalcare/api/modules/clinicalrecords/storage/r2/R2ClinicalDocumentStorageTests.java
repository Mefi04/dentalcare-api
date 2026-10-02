package com.dentalcare.api.modules.clinicalrecords.storage.r2;

import com.dentalcare.api.config.r2.R2Properties;
import com.dentalcare.api.modules.clinicalrecords.storage.StoredDocument;
import com.dentalcare.api.modules.clinicalrecords.storage.StoredDocumentContent;
import com.dentalcare.api.modules.clinicalrecords.storage.StoredDocumentMetadata;
import com.dentalcare.api.modules.clinicalrecords.storage.UploadDocumentCommand;
import com.dentalcare.api.modules.clinicalrecords.storage.exception.DocumentNotFoundInStorageException;
import com.dentalcare.api.modules.clinicalrecords.storage.exception.DocumentStorageException;
import com.dentalcare.api.modules.clinicalrecords.storage.exception.DocumentStorageUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class R2ClinicalDocumentStorageTests {

    private S3Client s3Client;
    private R2Properties properties;
    private R2ClinicalDocumentStorage storage;

    private static final String BUCKET_NAME = "dentalcare-expedientes";

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        properties = new R2Properties();
        properties.setBucket(BUCKET_NAME);
        properties.setEnabled(true);
        properties.setAccountId("test-account");
        properties.setEndpoint("https://test-account.r2.cloudflarestorage.com");
        properties.setAccessKeyId("test-access-key");
        properties.setSecretAccessKey("test-secret-key");

        storage = new R2ClinicalDocumentStorage(s3Client, properties);
    }

    @Test
    @DisplayName("store successfully uploads object and returns internal metadata model")
    void store_validCommand_uploadsObjectAndReturnsMetadata() {
        UUID patientId = UUID.randomUUID();
        byte[] bytes = "sample pdf content".getBytes(StandardCharsets.UTF_8);
        UploadDocumentCommand command = new UploadDocumentCommand(
                patientId,
                "radiografia-panoramica.pdf",
                "application/pdf",
                bytes.length,
                new ByteArrayInputStream(bytes)
        );

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        StoredDocument result = storage.store(command);

        assertThat(result).isNotNull();
        assertThat(result.fileName()).isEqualTo("radiografia-panoramica.pdf");
        assertThat(result.fileSize()).isEqualTo((long) bytes.length);
        assertThat(result.contentType()).isEqualTo("application/pdf");
        assertThat(result.storageObjectKey()).startsWith("patients/" + patientId + "/documents/");
        assertThat(result.storageObjectKey()).endsWith(".pdf");

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        PutObjectRequest sentRequest = requestCaptor.getValue();

        assertThat(sentRequest.bucket()).isEqualTo(BUCKET_NAME);
        assertThat(sentRequest.key()).isEqualTo(result.storageObjectKey());
        assertThat(sentRequest.contentType()).isEqualTo("application/pdf");
        assertThat(sentRequest.contentLength()).isEqualTo((long) bytes.length);
    }

    @Test
    @DisplayName("store sanitizes path traversal characters in original file name")
    void store_sanitizesFileNameAndPathTraversal() {
        UUID patientId = UUID.randomUUID();
        byte[] bytes = "data".getBytes(StandardCharsets.UTF_8);
        UploadDocumentCommand command = new UploadDocumentCommand(
                patientId,
                "../../suspicious/path/scan.png",
                "image/png",
                bytes.length,
                new ByteArrayInputStream(bytes)
        );

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        StoredDocument result = storage.store(command);

        assertThat(result.fileName()).isEqualTo("scan.png");
        assertThat(result.storageObjectKey()).startsWith("patients/" + patientId + "/documents/");
        assertThat(result.storageObjectKey()).endsWith(".png");
    }

    @Test
    @DisplayName("store handles files without extension safely")
    void store_whenNoExtension_generatesKeyWithoutExtension() {
        UUID patientId = UUID.randomUUID();
        byte[] bytes = "content".getBytes(StandardCharsets.UTF_8);
        UploadDocumentCommand command = new UploadDocumentCommand(
                patientId,
                "documento-sin-extension",
                "application/octet-stream",
                bytes.length,
                new ByteArrayInputStream(bytes)
        );

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        StoredDocument result = storage.store(command);

        assertThat(result.fileName()).isEqualTo("documento-sin-extension");
        assertThat(result.storageObjectKey()).matches("^patients/" + patientId + "/documents/[0-9a-fA-F\\-]+$");
    }

    @Test
    @DisplayName("store avoids collision by generating unique keys for identical patient and file name")
    void store_generatesUniqueKeysForSamePatientAndName() {
        UUID patientId = UUID.randomUUID();
        String key1 = storage.generateObjectKey(patientId, "file.pdf");
        String key2 = storage.generateObjectKey(patientId, "file.pdf");

        assertThat(key1).isNotEqualTo(key2);
        assertThat(key1).startsWith("patients/" + patientId + "/documents/");
        assertThat(key2).startsWith("patients/" + patientId + "/documents/");
    }

    @Test
    @DisplayName("store translates S3Exception (403 forbidden) to DocumentStorageException without leaking secrets")
    void store_s3ExceptionForbidden_translatesToDocumentStorageException() {
        UUID patientId = UUID.randomUUID();
        UploadDocumentCommand command = new UploadDocumentCommand(
                patientId,
                "test.pdf",
                "application/pdf",
                10L,
                new ByteArrayInputStream("data".getBytes())
        );

        S3Exception s3Exception = (S3Exception) S3Exception.builder()
                .statusCode(403)
                .message("Access Denied with secret=AKIAEXAMPLE")
                .build();

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(s3Exception);

        assertThatThrownBy(() -> storage.store(command))
                .isInstanceOf(DocumentStorageException.class)
                .hasMessage("Access denied to storage service");
    }

    @Test
    @DisplayName("store translates SdkClientException to DocumentStorageUnavailableException")
    void store_sdkClientException_translatesToDocumentStorageUnavailableException() {
        UUID patientId = UUID.randomUUID();
        UploadDocumentCommand command = new UploadDocumentCommand(
                patientId,
                "test.pdf",
                "application/pdf",
                10L,
                new ByteArrayInputStream("data".getBytes())
        );

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("Connection timed out"));

        assertThatThrownBy(() -> storage.store(command))
                .isInstanceOf(DocumentStorageUnavailableException.class)
                .hasMessage("Storage service is currently unavailable");
    }

    @Test
    @DisplayName("load retrieves document stream and metadata cleanly")
    void load_validKey_returnsContentStreamAndMetadata() throws IOException {
        String key = "patients/123/documents/abc.pdf";
        byte[] expectedBytes = "sample streamed content".getBytes(StandardCharsets.UTF_8);

        GetObjectResponse getResponse = GetObjectResponse.builder()
                .contentType("application/pdf")
                .contentLength((long) expectedBytes.length)
                .build();

        ResponseInputStream<GetObjectResponse> responseStream = new ResponseInputStream<>(
                getResponse,
                AbortableInputStream.create(new ByteArrayInputStream(expectedBytes))
        );

        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseStream);

        try (StoredDocumentContent content = storage.load(key)) {
            assertThat(content).isNotNull();
            assertThat(content.storageObjectKey()).isEqualTo(key);
            assertThat(content.contentType()).isEqualTo("application/pdf");
            assertThat(content.contentLength()).isEqualTo((long) expectedBytes.length);

            byte[] readBytes = content.content().readAllBytes();
            assertThat(readBytes).isEqualTo(expectedBytes);
        }

        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET_NAME);
        assertThat(captor.getValue().key()).isEqualTo(key);
    }

    @Test
    @DisplayName("load throws DocumentNotFoundInStorageException when key does not exist")
    void load_notFound_throwsDocumentNotFoundInStorageException() {
        String key = "patients/123/documents/missing.pdf";

        NoSuchKeyException noSuchKey = (NoSuchKeyException) NoSuchKeyException.builder()
                .statusCode(404)
                .message("Key not found")
                .build();

        when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(noSuchKey);

        assertThatThrownBy(() -> storage.load(key))
                .isInstanceOf(DocumentNotFoundInStorageException.class)
                .hasMessage("Document not found in storage");
    }

    @Test
    @DisplayName("load rejects blank storageObjectKey")
    void load_blankKey_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> storage.load("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Storage object key is required");
    }

    @Test
    @DisplayName("getMetadata retrieves HEAD metadata correctly")
    void getMetadata_validKey_returnsMetadata() {
        String key = "patients/123/documents/meta.pdf";
        Instant modified = Instant.parse("2026-10-02T12:00:00Z");

        HeadObjectResponse headResponse = HeadObjectResponse.builder()
                .contentLength(2048L)
                .contentType("application/pdf")
                .lastModified(modified)
                .build();

        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(headResponse);

        StoredDocumentMetadata metadata = storage.getMetadata(key);

        assertThat(metadata).isNotNull();
        assertThat(metadata.storageObjectKey()).isEqualTo(key);
        assertThat(metadata.contentLength()).isEqualTo(2048L);
        assertThat(metadata.contentType()).isEqualTo("application/pdf");
        assertThat(metadata.lastModified()).isEqualTo(modified);
    }

    @Test
    @DisplayName("getMetadata throws DocumentNotFoundInStorageException when object does not exist")
    void getMetadata_notFound_throwsDocumentNotFoundInStorageException() {
        String key = "patients/123/documents/missing.pdf";

        S3Exception notFound = (S3Exception) S3Exception.builder()
                .statusCode(404)
                .message("Not found")
                .build();

        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(notFound);

        assertThatThrownBy(() -> storage.getMetadata(key))
                .isInstanceOf(DocumentNotFoundInStorageException.class)
                .hasMessage("Document not found in storage");
    }

    @Test
    @DisplayName("exists returns true when object exists and false when not found or blank")
    void exists_behavesCorrectly() {
        String existingKey = "patients/123/documents/exists.pdf";
        String missingKey = "patients/123/documents/missing.pdf";

        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());

        assertThat(storage.exists(existingKey)).isTrue();
        assertThat(storage.exists(null)).isFalse();
        assertThat(storage.exists("   ")).isFalse();

        NoSuchKeyException noSuchKey = (NoSuchKeyException) NoSuchKeyException.builder()
                .statusCode(404)
                .message("Key not found")
                .build();
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(noSuchKey);

        assertThat(storage.exists(missingKey)).isFalse();
    }

    @Test
    @DisplayName("delete executes s3Client.deleteObject with correct bucket and key")
    void delete_validKey_callsS3ClientDeleteObject() {
        String key = "patients/123/documents/to-delete.pdf";

        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());

        storage.delete(key);

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET_NAME);
        assertThat(captor.getValue().key()).isEqualTo(key);
    }

    @Test
    @DisplayName("delete is idempotent: does not fail if object does not exist in storage")
    void delete_whenNotFound_isIdempotent() {
        String key = "patients/123/documents/already-deleted.pdf";

        NoSuchKeyException noSuchKey = (NoSuchKeyException) NoSuchKeyException.builder()
                .statusCode(404)
                .message("Key not found")
                .build();

        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(noSuchKey);

        // Must not throw
        storage.delete(key);
    }

    @Test
    @DisplayName("delete rejects blank storageObjectKey")
    void delete_blankKey_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> storage.delete("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Storage object key is required");
    }
}
