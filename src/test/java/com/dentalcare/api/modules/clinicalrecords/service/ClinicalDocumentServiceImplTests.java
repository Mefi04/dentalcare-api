package com.dentalcare.api.modules.clinicalrecords.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.exception.UnauthorizedException;
import com.dentalcare.api.modules.clinicalrecords.dto.request.CreateClinicalDocumentRequest;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalDocumentResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalProfessionalResponse;
import com.dentalcare.api.modules.clinicalrecords.mapper.ClinicalDocumentMapper;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDocument;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDocumentType;
import com.dentalcare.api.modules.clinicalrecords.repository.ClinicalDocumentRepository;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClinicalDocumentServiceImplTests {

    @Mock private ClinicalDocumentRepository clinicalDocumentRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private UserRepository userRepository;
    @Mock private ClinicalDocumentMapper mapper;

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
        // Repository filters by documentId AND patientId, returning empty
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
    }
}
