package com.dentalcare.api.modules.clinicalrecords.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.exception.UnauthorizedException;
import com.dentalcare.api.modules.clinicalrecords.dto.request.CreateClinicalDocumentRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.request.UploadClinicalDocumentRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalDocumentDownload;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalDocumentResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalProfessionalResponse;
import com.dentalcare.api.modules.clinicalrecords.mapper.ClinicalDocumentMapper;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDocument;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDocumentType;
import com.dentalcare.api.modules.clinicalrecords.repository.ClinicalDocumentRepository;
import com.dentalcare.api.modules.clinicalrecords.storage.ClinicalDocumentStorage;
import com.dentalcare.api.modules.clinicalrecords.storage.StoredDocument;
import com.dentalcare.api.modules.clinicalrecords.storage.StoredDocumentContent;
import com.dentalcare.api.modules.clinicalrecords.storage.UploadDocumentCommand;
import com.dentalcare.api.modules.clinicalrecords.storage.exception.DocumentNotFoundInStorageException;
import com.dentalcare.api.modules.clinicalrecords.storage.exception.DocumentStorageUnavailableException;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClinicalDocumentServiceImplTests {

    @Mock private ClinicalDocumentRepository clinicalDocumentRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private UserRepository userRepository;
    @Mock private ClinicalDocumentMapper mapper;
    @Mock private ClinicalDocumentStorage clinicalDocumentStorage;
    @Mock private ClinicalDocumentFileValidator fileValidator;

    private Clock clock;
    private ClinicalDocumentServiceImpl service;

    private final Instant fixedInstant = Instant.parse("2026-10-01T14:30:00Z");

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
        service = new ClinicalDocumentServiceImpl(
                clinicalDocumentRepository,
                patientRepository,
                userRepository,
                mapper,
                clinicalDocumentStorage,
                fileValidator,
                clock
        );
    }

    @Test
    @DisplayName("createDocument successfully saves document with audit author from authenticated user")
    void createDocument_withValidRequest_savesAndReturnsResponse() {
        UUID patientId = UUID.randomUUID();
        UUID authUserId = UUID.randomUUID();

        Patient patient = new Patient();
        patient.setId(patientId);

        User author = new User();
        author.setId(authUserId);
        author.setStatus(UserStatus.ACTIVE);

        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
        when(userRepository.findById(authUserId)).thenReturn(Optional.of(author));

        LocalDate docDate = LocalDate.of(2026, 9, 28);
        CreateClinicalDocumentRequest request = new CreateClinicalDocumentRequest(
                "  Radiografía panorámica inicial  ",
                ClinicalDocumentType.RADIOGRAPHY,
                "  Estudio previo a ortodoncia  ",
                docDate
        );

        when(clinicalDocumentRepository.save(any(ClinicalDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(ClinicalDocument.class))).thenReturn(new ClinicalDocumentResponse(
                UUID.randomUUID(),
                patientId,
                new ClinicalProfessionalResponse(authUserId, "Dr. Roberto"),
                "Radiografía panorámica inicial",
                ClinicalDocumentType.RADIOGRAPHY,
                "Estudio previo a ortodoncia",
                docDate,
                fixedInstant,
                fixedInstant
        ));

        ClinicalDocumentResponse result = service.createDocument(patientId, request, authUserId);

        assertThat(result).isNotNull();
        assertThat(result.title()).isEqualTo("Radiografía panorámica inicial");

        ArgumentCaptor<ClinicalDocument> captor = ArgumentCaptor.forClass(ClinicalDocument.class);
        verify(clinicalDocumentRepository).save(captor.capture());
        ClinicalDocument saved = captor.getValue();

        assertThat(saved.getPatient()).isEqualTo(patient);
        assertThat(saved.getAuthor()).isEqualTo(author);
        assertThat(saved.getTitle()).isEqualTo("Radiografía panorámica inicial");
        assertThat(saved.getType()).isEqualTo(ClinicalDocumentType.RADIOGRAPHY);
        assertThat(saved.getDescription()).isEqualTo("Estudio previo a ortodoncia");
        assertThat(saved.getDocumentDate()).isEqualTo(docDate);
        assertThat(saved.getCreatedAt()).isEqualTo(fixedInstant);
        assertThat(saved.getUpdatedAt()).isEqualTo(fixedInstant);
        assertThat(saved.getStorageObjectKey()).isNull();
        assertThat(saved.getFileName()).isNull();
        assertThat(saved.getFileSize()).isNull();
        assertThat(saved.getContentType()).isNull();
        assertThat(saved.hasFile()).isFalse();
    }

    @Test
    @DisplayName("createDocument defaults documentDate to clock date when documentDate is null")
    void createDocument_withNullDocumentDate_defaultsToClockDate() {
        UUID patientId = UUID.randomUUID();
        UUID authUserId = UUID.randomUUID();

        Patient patient = new Patient();
        patient.setId(patientId);

        User author = new User();
        author.setId(authUserId);
        author.setStatus(UserStatus.ACTIVE);

        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
        when(userRepository.findById(authUserId)).thenReturn(Optional.of(author));

        CreateClinicalDocumentRequest request = new CreateClinicalDocumentRequest(
                "Consentimiento informado",
                ClinicalDocumentType.INFORMED_CONSENT,
                null,
                null
        );

        when(clinicalDocumentRepository.save(any(ClinicalDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createDocument(patientId, request, authUserId);

        ArgumentCaptor<ClinicalDocument> captor = ArgumentCaptor.forClass(ClinicalDocument.class);
        verify(clinicalDocumentRepository).save(captor.capture());
        ClinicalDocument saved = captor.getValue();

        assertThat(saved.getDocumentDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(saved.getDescription()).isNull();
    }

    @Test
    @DisplayName("createDocument throws ResourceNotFoundException when patient does not exist")
    void createDocument_whenPatientNotFound_throwsResourceNotFound() {
        UUID patientId = UUID.randomUUID();
        UUID authUserId = UUID.randomUUID();

        when(patientRepository.findById(patientId)).thenReturn(Optional.empty());

        CreateClinicalDocumentRequest request = new CreateClinicalDocumentRequest(
                "Documento",
                ClinicalDocumentType.OTHER,
                null,
                null
        );

        assertThatThrownBy(() -> service.createDocument(patientId, request, authUserId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Patient not found");
    }

    @Test
    @DisplayName("createDocument throws UnauthorizedException when author user does not exist")
    void createDocument_whenAuthorNotFound_throwsUnauthorized() {
        UUID patientId = UUID.randomUUID();
        UUID authUserId = UUID.randomUUID();

        Patient patient = new Patient();
        patient.setId(patientId);

        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
        when(userRepository.findById(authUserId)).thenReturn(Optional.empty());

        CreateClinicalDocumentRequest request = new CreateClinicalDocumentRequest(
                "Documento",
                ClinicalDocumentType.OTHER,
                null,
                null
        );

        assertThatThrownBy(() -> service.createDocument(patientId, request, authUserId))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Authenticated user not found");
    }

    @Test
    @DisplayName("createDocument throws UnauthorizedException when author is inactive")
    void createDocument_whenAuthorInactive_throwsUnauthorized() {
        UUID patientId = UUID.randomUUID();
        UUID authUserId = UUID.randomUUID();

        Patient patient = new Patient();
        patient.setId(patientId);

        User author = new User();
        author.setId(authUserId);
        author.setStatus(UserStatus.INACTIVE);

        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
        when(userRepository.findById(authUserId)).thenReturn(Optional.of(author));

        CreateClinicalDocumentRequest request = new CreateClinicalDocumentRequest(
                "Documento",
                ClinicalDocumentType.OTHER,
                null,
                null
        );

        assertThatThrownBy(() -> service.createDocument(patientId, request, authUserId))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Authenticated user is not active");
    }

    @Test
    @DisplayName("uploadDocument successfully uploads to storage, persists metadata, and returns response")
    void uploadDocument_withValidRequest_uploadsToStorageAndPersistsMetadata() {
        UUID patientId = UUID.randomUUID();
        UUID authUserId = UUID.randomUUID();

        Patient patient = new Patient();
        patient.setId(patientId);

        User author = new User();
        author.setId(authUserId);
        author.setStatus(UserStatus.ACTIVE);

        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
        when(userRepository.findById(authUserId)).thenReturn(Optional.of(author));

        MockMultipartFile file = new MockMultipartFile(
                "file", "radiografia.pdf", "application/pdf", "%PDF-1.4 test".getBytes(StandardCharsets.UTF_8));

        UploadClinicalDocumentRequest request = new UploadClinicalDocumentRequest(
                file,
                "Radiografía panorámica inicial",
                ClinicalDocumentType.RADIOGRAPHY,
                "Estudio previo a ortodoncia",
                LocalDate.of(2026, 9, 28)
        );

        String generatedKey = "patients/" + patientId + "/documents/" + UUID.randomUUID() + ".pdf";
        StoredDocument stored = new StoredDocument(generatedKey, "radiografia.pdf", file.getSize(), "application/pdf");
        when(clinicalDocumentStorage.store(any(UploadDocumentCommand.class))).thenReturn(stored);
        when(clinicalDocumentRepository.saveAndFlush(any(ClinicalDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClinicalDocumentResponse expectedResponse = new ClinicalDocumentResponse(
                UUID.randomUUID(),
                patientId,
                new ClinicalProfessionalResponse(authUserId, "Dr. Roberto"),
                "Radiografía panorámica inicial",
                ClinicalDocumentType.RADIOGRAPHY,
                "Estudio previo a ortodoncia",
                LocalDate.of(2026, 9, 28),
                "radiografia.pdf",
                file.getSize(),
                "application/pdf",
                true,
                fixedInstant,
                fixedInstant
        );
        when(mapper.toResponse(any(ClinicalDocument.class))).thenReturn(expectedResponse);

        ClinicalDocumentResponse response = service.uploadDocument(patientId, request, authUserId);

        assertThat(response).isNotNull();
        assertThat(response.hasFile()).isTrue();
        assertThat(response.fileName()).isEqualTo("radiografia.pdf");

        verify(fileValidator).validate(file);
        verify(clinicalDocumentStorage).store(any(UploadDocumentCommand.class));

        ArgumentCaptor<ClinicalDocument> docCaptor = ArgumentCaptor.forClass(ClinicalDocument.class);
        verify(clinicalDocumentRepository).saveAndFlush(docCaptor.capture());
        ClinicalDocument captured = docCaptor.getValue();

        assertThat(captured.getStorageObjectKey()).isEqualTo(generatedKey);
        assertThat(captured.getFileName()).isEqualTo("radiografia.pdf");
        assertThat(captured.getFileSize()).isEqualTo(file.getSize());
        assertThat(captured.getContentType()).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("uploadDocument throws BadRequestException when fileValidator rejects file without touching storage")
    void uploadDocument_whenFileValidatorFails_doesNotCallStorageOrRepository() {
        UUID patientId = UUID.randomUUID();
        UUID authUserId = UUID.randomUUID();

        MockMultipartFile file = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);
        UploadClinicalDocumentRequest request = new UploadClinicalDocumentRequest(
                file, "Titulo", ClinicalDocumentType.OTHER, null, null);

        doThrow(new BadRequestException("File must not be empty")).when(fileValidator).validate(file);

        assertThatThrownBy(() -> service.uploadDocument(patientId, request, authUserId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("File must not be empty");

        verify(clinicalDocumentStorage, never()).store(any());
        verify(clinicalDocumentRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("uploadDocument throws ResourceNotFoundException when patient not found without calling storage")
    void uploadDocument_whenPatientNotFound_throwsResourceNotFoundWithoutCallingStorage() {
        UUID patientId = UUID.randomUUID();
        UUID authUserId = UUID.randomUUID();

        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "%PDF".getBytes());
        UploadClinicalDocumentRequest request = new UploadClinicalDocumentRequest(
                file, "Titulo", ClinicalDocumentType.OTHER, null, null);

        when(patientRepository.findById(patientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.uploadDocument(patientId, request, authUserId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Patient not found");

        verify(clinicalDocumentStorage, never()).store(any());
    }

    @Test
    @DisplayName("uploadDocument throws UnauthorizedException when user inactive without calling storage")
    void uploadDocument_whenUserInactive_throwsUnauthorizedWithoutCallingStorage() {
        UUID patientId = UUID.randomUUID();
        UUID authUserId = UUID.randomUUID();

        Patient patient = new Patient();
        patient.setId(patientId);

        User inactiveUser = new User();
        inactiveUser.setId(authUserId);
        inactiveUser.setStatus(UserStatus.INACTIVE);

        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
        when(userRepository.findById(authUserId)).thenReturn(Optional.of(inactiveUser));

        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "%PDF".getBytes());
        UploadClinicalDocumentRequest request = new UploadClinicalDocumentRequest(
                file, "Titulo", ClinicalDocumentType.OTHER, null, null);

        assertThatThrownBy(() -> service.uploadDocument(patientId, request, authUserId))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Authenticated user is not active");

        verify(clinicalDocumentStorage, never()).store(any());
    }

    @Test
    @DisplayName("uploadDocument does not persist in DB when storage upload fails")
    void uploadDocument_whenStorageFails_doesNotPersistInDatabase() {
        UUID patientId = UUID.randomUUID();
        UUID authUserId = UUID.randomUUID();

        Patient patient = new Patient();
        patient.setId(patientId);

        User author = new User();
        author.setId(authUserId);
        author.setStatus(UserStatus.ACTIVE);

        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
        when(userRepository.findById(authUserId)).thenReturn(Optional.of(author));

        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "%PDF".getBytes());
        UploadClinicalDocumentRequest request = new UploadClinicalDocumentRequest(
                file, "Titulo", ClinicalDocumentType.OTHER, null, null);

        when(clinicalDocumentStorage.store(any())).thenThrow(new DocumentStorageUnavailableException("Storage unavailable", null));

        assertThatThrownBy(() -> service.uploadDocument(patientId, request, authUserId))
                .isInstanceOf(DocumentStorageUnavailableException.class);

        verify(clinicalDocumentRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("uploadDocument executes compensating delete on R2 when database persistence fails")
    void uploadDocument_whenDatabasePersistenceFails_executesCompensatingDelete() {
        UUID patientId = UUID.randomUUID();
        UUID authUserId = UUID.randomUUID();

        Patient patient = new Patient();
        patient.setId(patientId);

        User author = new User();
        author.setId(authUserId);
        author.setStatus(UserStatus.ACTIVE);

        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
        when(userRepository.findById(authUserId)).thenReturn(Optional.of(author));

        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "%PDF".getBytes());
        UploadClinicalDocumentRequest request = new UploadClinicalDocumentRequest(
                file, "Titulo", ClinicalDocumentType.OTHER, null, null);

        String generatedKey = "patients/" + patientId + "/documents/orphaned.pdf";
        StoredDocument stored = new StoredDocument(generatedKey, "doc.pdf", file.getSize(), "application/pdf");
        when(clinicalDocumentStorage.store(any())).thenReturn(stored);

        // Database failure
        when(clinicalDocumentRepository.saveAndFlush(any()))
                .thenThrow(new RuntimeException("Database constraint violation or timeout"));

        assertThatThrownBy(() -> service.uploadDocument(patientId, request, authUserId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Database constraint violation or timeout");

        // Compensating delete MUST be executed with the exact generated key
        verify(clinicalDocumentStorage).delete(generatedKey);
    }

    @Test
    @DisplayName("uploadDocument does not mask original DB exception when compensating delete also fails")
    void uploadDocument_whenCompensatingDeleteFails_rethrowsOriginalException() {
        UUID patientId = UUID.randomUUID();
        UUID authUserId = UUID.randomUUID();

        Patient patient = new Patient();
        patient.setId(patientId);

        User author = new User();
        author.setId(authUserId);
        author.setStatus(UserStatus.ACTIVE);

        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
        when(userRepository.findById(authUserId)).thenReturn(Optional.of(author));

        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "%PDF".getBytes());
        UploadClinicalDocumentRequest request = new UploadClinicalDocumentRequest(
                file, "Titulo", ClinicalDocumentType.OTHER, null, null);

        String generatedKey = "patients/" + patientId + "/documents/orphaned.pdf";
        StoredDocument stored = new StoredDocument(generatedKey, "doc.pdf", file.getSize(), "application/pdf");
        when(clinicalDocumentStorage.store(any())).thenReturn(stored);

        when(clinicalDocumentRepository.saveAndFlush(any()))
                .thenThrow(new RuntimeException("Original DB failure"));

        doThrow(new RuntimeException("Compensating delete failed")).when(clinicalDocumentStorage).delete(generatedKey);

        assertThatThrownBy(() -> service.uploadDocument(patientId, request, authUserId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Original DB failure");
    }

    @Test
    @DisplayName("downloadDocument successfully returns stream and metadata when document has file")
    void downloadDocument_withValidDocument_returnsDownload() {
        UUID patientId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        when(patientRepository.existsById(patientId)).thenReturn(true);

        String objectKey = "patients/" + patientId + "/documents/test.pdf";
        ClinicalDocument doc = new ClinicalDocument(
                documentId,
                new Patient(),
                new User(),
                "Radiografia",
                ClinicalDocumentType.RADIOGRAPHY,
                "Desc",
                LocalDate.now(),
                fixedInstant,
                fixedInstant,
                objectKey,
                "radiografia.pdf",
                1024L,
                "application/pdf"
        );
        when(clinicalDocumentRepository.findByIdAndPatient_Id(documentId, patientId)).thenReturn(Optional.of(doc));

        ByteArrayInputStream stream = new ByteArrayInputStream("%PDF-1.4 stream".getBytes(StandardCharsets.UTF_8));
        StoredDocumentContent content = new StoredDocumentContent(objectKey, "application/pdf", 1024L, stream);
        when(clinicalDocumentStorage.load(objectKey)).thenReturn(content);

        ClinicalDocumentDownload download = service.downloadDocument(patientId, documentId);

        assertThat(download).isNotNull();
        assertThat(download.fileName()).isEqualTo("radiografia.pdf");
        assertThat(download.contentType()).isEqualTo("application/pdf");
        assertThat(download.contentLength()).isEqualTo(1024L);
        assertThat(download.inputStream()).isSameAs(stream);
    }

    @Test
    @DisplayName("downloadDocument throws ResourceNotFoundException when document has no file (legacy)")
    void downloadDocument_whenDocumentHasNoFile_throwsResourceNotFound() {
        UUID patientId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        when(patientRepository.existsById(patientId)).thenReturn(true);

        ClinicalDocument legacyDoc = new ClinicalDocument(
                documentId,
                new Patient(),
                new User(),
                "Nota clínica",
                ClinicalDocumentType.OTHER,
                "Legacy sin archivo",
                LocalDate.now(),
                fixedInstant,
                fixedInstant
        );
        when(clinicalDocumentRepository.findByIdAndPatient_Id(documentId, patientId)).thenReturn(Optional.of(legacyDoc));

        assertThatThrownBy(() -> service.downloadDocument(patientId, documentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Clinical document does not have an attached file");

        verify(clinicalDocumentStorage, never()).load(any());
    }

    @Test
    @DisplayName("downloadDocument prevents IDOR and throws ResourceNotFoundException when document belongs to another patient")
    void downloadDocument_whenDocumentBelongsToAnotherPatient_throwsResourceNotFound() {
        UUID patientId = UUID.randomUUID(); // Patient B
        UUID documentId = UUID.randomUUID(); // Document owned by Patient A

        when(patientRepository.existsById(patientId)).thenReturn(true);
        when(clinicalDocumentRepository.findByIdAndPatient_Id(documentId, patientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.downloadDocument(patientId, documentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Clinical document not found");

        verify(clinicalDocumentStorage, never()).load(any());
    }

    @Test
    @DisplayName("downloadDocument throws DocumentNotFoundInStorageException when storage object is missing")
    void downloadDocument_whenStorageObjectMissing_propagatesStorageException() {
        UUID patientId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        when(patientRepository.existsById(patientId)).thenReturn(true);

        String objectKey = "patients/" + patientId + "/documents/missing.pdf";
        ClinicalDocument doc = new ClinicalDocument(
                documentId, new Patient(), new User(), "Doc", ClinicalDocumentType.OTHER, null,
                LocalDate.now(), fixedInstant, fixedInstant, objectKey, "missing.pdf", 100L, "application/pdf"
        );
        when(clinicalDocumentRepository.findByIdAndPatient_Id(documentId, patientId)).thenReturn(Optional.of(doc));
        when(clinicalDocumentStorage.load(objectKey)).thenThrow(new DocumentNotFoundInStorageException("Object missing in storage", null));

        assertThatThrownBy(() -> service.downloadDocument(patientId, documentId))
                .isInstanceOf(DocumentNotFoundInStorageException.class)
                .hasMessageContaining("Object missing in storage");
    }

    @Test
    @DisplayName("findDocumentsByPatient returns paginated documents")
    void findDocumentsByPatient_returnsPaginatedResults() {
        UUID patientId = UUID.randomUUID();
        when(patientRepository.existsById(patientId)).thenReturn(true);

        ClinicalDocument doc = new ClinicalDocument();
        Page<ClinicalDocument> page = new PageImpl<>(List.of(doc));
        when(clinicalDocumentRepository.findByPatient_Id(eq(patientId), any(Pageable.class))).thenReturn(page);
        when(mapper.toResponse(doc)).thenReturn(mock(ClinicalDocumentResponse.class));

        Page<ClinicalDocumentResponse> results = service.findDocumentsByPatient(patientId, null, 0, 20);

        assertThat(results).hasSize(1);
    }

    @Test
    @DisplayName("findDocumentsByPatient with type filter queries by patient and type")
    void findDocumentsByPatient_withTypeFilter_queriesByPatientAndType() {
        UUID patientId = UUID.randomUUID();
        when(patientRepository.existsById(patientId)).thenReturn(true);

        ClinicalDocument doc = new ClinicalDocument();
        Page<ClinicalDocument> page = new PageImpl<>(List.of(doc));
        when(clinicalDocumentRepository.findByPatient_IdAndType(eq(patientId), eq(ClinicalDocumentType.RADIOGRAPHY), any(Pageable.class)))
                .thenReturn(page);
        when(mapper.toResponse(doc)).thenReturn(mock(ClinicalDocumentResponse.class));

        Page<ClinicalDocumentResponse> results = service.findDocumentsByPatient(patientId, ClinicalDocumentType.RADIOGRAPHY, 0, 20);

        assertThat(results).hasSize(1);
    }

    @Test
    @DisplayName("findDocumentsByPatient throws ResourceNotFoundException when patient does not exist")
    void findDocumentsByPatient_whenPatientNotFound_throwsResourceNotFound() {
        UUID patientId = UUID.randomUUID();
        when(patientRepository.existsById(patientId)).thenReturn(false);

        assertThatThrownBy(() -> service.findDocumentsByPatient(patientId, null, 0, 20))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Patient not found");
    }

    @Test
    @DisplayName("findDocumentById returns document when patient and document match")
    void findDocumentById_whenFound_returnsResponse() {
        UUID patientId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        when(patientRepository.existsById(patientId)).thenReturn(true);
        ClinicalDocument doc = new ClinicalDocument();
        when(clinicalDocumentRepository.findByIdAndPatient_Id(documentId, patientId)).thenReturn(Optional.of(doc));

        ClinicalDocumentResponse expectedResponse = mock(ClinicalDocumentResponse.class);
        when(mapper.toResponse(doc)).thenReturn(expectedResponse);

        ClinicalDocumentResponse result = service.findDocumentById(patientId, documentId);

        assertThat(result).isSameAs(expectedResponse);
    }

    @Test
    @DisplayName("findDocumentById throws ResourceNotFoundException (preventing IDOR) when document belongs to another patient")
    void findDocumentById_whenBelongsToAnotherPatient_throwsResourceNotFoundWithoutLeak() {
        UUID patientId = UUID.randomUUID(); // Patient B
        UUID documentId = UUID.randomUUID(); // Document owned by Patient A

        when(patientRepository.existsById(patientId)).thenReturn(true);
        when(clinicalDocumentRepository.findByIdAndPatient_Id(documentId, patientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findDocumentById(patientId, documentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Clinical document not found");
    }

    @Test
    @DisplayName("findDocumentById throws ResourceNotFoundException when patient does not exist")
    void findDocumentById_whenPatientNotFound_throwsResourceNotFound() {
        UUID patientId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        when(patientRepository.existsById(patientId)).thenReturn(false);

        assertThatThrownBy(() -> service.findDocumentById(patientId, documentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Patient not found");
    }

    @Test
    @DisplayName("Validation fails when required ID parameters are null")
    void requireId_validatesNullParameters() {
        UUID patientId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        assertThatThrownBy(() -> service.findDocumentById(null, documentId))
                .isInstanceOf(BadRequestException.class);

        assertThatThrownBy(() -> service.findDocumentById(patientId, null))
                .isInstanceOf(BadRequestException.class);

        assertThatThrownBy(() -> service.createDocument(null, mock(CreateClinicalDocumentRequest.class), UUID.randomUUID()))
                .isInstanceOf(BadRequestException.class);

        assertThatThrownBy(() -> service.createDocument(patientId, mock(CreateClinicalDocumentRequest.class), null))
                .isInstanceOf(BadRequestException.class);

        assertThatThrownBy(() -> service.uploadDocument(null, mock(UploadClinicalDocumentRequest.class), UUID.randomUUID()))
                .isInstanceOf(BadRequestException.class);

        assertThatThrownBy(() -> service.uploadDocument(patientId, mock(UploadClinicalDocumentRequest.class), null))
                .isInstanceOf(BadRequestException.class);

        assertThatThrownBy(() -> service.downloadDocument(null, documentId))
                .isInstanceOf(BadRequestException.class);

        assertThatThrownBy(() -> service.downloadDocument(patientId, null))
                .isInstanceOf(BadRequestException.class);
    }
}
