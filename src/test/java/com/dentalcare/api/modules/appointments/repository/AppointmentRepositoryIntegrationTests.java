package com.dentalcare.api.modules.appointments.repository;

import com.dentalcare.api.modules.appointments.model.Appointment;
import com.dentalcare.api.modules.appointments.model.AppointmentStatus;
import com.dentalcare.api.modules.appointments.model.AppointmentRequest;
import com.dentalcare.api.modules.appointments.model.AppointmentRequestStatus;
import com.dentalcare.api.modules.appointments.dto.response.AppointmentRequestResponse;
import com.dentalcare.api.modules.appointments.mapper.AppointmentRequestMapper;
import com.dentalcare.api.modules.appointments.model.WaitingRoomEntry;
import com.dentalcare.api.modules.appointments.model.AppointmentRequestMessage;
import com.dentalcare.api.modules.appointments.model.AppointmentPublicConversation;
import com.dentalcare.api.modules.appointments.model.AppointmentNotificationOutboxEvent;
import com.dentalcare.api.modules.appointments.model.AppointmentNotificationStatus;
import com.dentalcare.api.modules.appointments.repository.AppointmentRequestMessageRepository;
import com.dentalcare.api.modules.appointments.repository.AppointmentNotificationOutboxRepository;
import com.dentalcare.api.modules.patients.model.Gender;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.users.model.Role;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.RoleRepository;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class AppointmentRepositoryIntegrationTests {
    private static final AtomicLong USER_CUI_SEQUENCE = new AtomicLong(2_100_000_000_100L);
    private static final AtomicLong PATIENT_DPI_SEQUENCE = new AtomicLong(2_200_000_000_100L);
    private static final Instant NOW = Instant.parse("2026-09-27T12:00:00Z");
    private static final Instant SCHEDULED_AT = Instant.parse("2026-09-28T15:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired AppointmentRepository appointments;
    @Autowired AppointmentRequestRepository appointmentRequests;
    @Autowired AppointmentRequestMessageRepository messages;
    @Autowired AppointmentPublicConversationRepository publicConversations;
    @Autowired AppointmentNotificationOutboxRepository notificationOutbox;
    @Autowired WaitingRoomRepository waitingRoom;
    @Autowired PatientRepository patients;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    private Patient patient;
    private User dentist;

    @BeforeEach
    void setUp() {
        Role dentistRole = roles.findByCode("DENTIST").orElseThrow();
        dentist = users.save(user(dentistRole));
        patient = patients.save(patient());
    }

    @Test
    void persistsAndRecoversAppointmentWithItsRelationshipsAndInitialStatus() {
        UUID id = UUID.randomUUID();
        appointments.saveAndFlush(new Appointment(id, patient, dentist, SCHEDULED_AT,
                AppointmentStatus.SCHEDULED, NOW, NOW));

        Appointment recovered = appointments.findById(id).orElseThrow();

        assertThat(recovered.getPatient().getId()).isEqualTo(patient.getId());
        assertThat(recovered.getProfessional().getId()).isEqualTo(dentist.getId());
        assertThat(recovered.getScheduledAt()).isEqualTo(SCHEDULED_AT);
        assertThat(recovered.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
        assertThat(recovered.getCreatedAt()).isEqualTo(NOW);
        assertThat(recovered.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void persistsExplicitSupportedStatus() {
        UUID id = UUID.randomUUID();
        appointments.saveAndFlush(new Appointment(id, patient, dentist, SCHEDULED_AT,
                AppointmentStatus.COMPLETED, NOW, NOW));

        assertThat(appointments.findById(id).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.COMPLETED);
    }

    @Test
    void postgresUniqueIndexPreventsTwoScheduledAppointmentsForSameDentistAndInstant() {
        appointments.saveAndFlush(new Appointment(UUID.randomUUID(), patient, dentist, SCHEDULED_AT,
                AppointmentStatus.SCHEDULED, NOW, NOW));

        assertThatThrownBy(() -> appointments.saveAndFlush(new Appointment(UUID.randomUUID(), patient, dentist,
                SCHEDULED_AT, AppointmentStatus.SCHEDULED, NOW, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void concurrentPostgresReservationsForSameDentistAndInstantHaveOnlyOneWinner() throws Exception {
        TestTransaction.flagForCommit();
        TestTransaction.end();
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> reserve = () -> {
                try {
                    return Boolean.TRUE.equals(new TransactionTemplate(transactionManager).execute(status -> {
                        appointments.saveAndFlush(new Appointment(UUID.randomUUID(), patient, dentist, SCHEDULED_AT,
                                AppointmentStatus.SCHEDULED, NOW, NOW));
                        return true;
                    }));
                } catch (DataIntegrityViolationException conflict) {
                    return false;
                }
            };
            var first = pool.submit(reserve);
            var second = pool.submit(reserve);
            assertThat((first.get() ? 1 : 0) + (second.get() ? 1 : 0)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from appointments where professional_id = ? and scheduled_at = ? and status = 'SCHEDULED'",
                    Integer.class, dentist.getId(), Timestamp.from(SCHEDULED_AT))).isEqualTo(1);
        } finally {
            pool.shutdownNow();
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                jdbc.update("delete from appointment_waiting_room_entries where appointment_id in " +
                        "(select id from appointments where patient_id = ? and professional_id = ? and scheduled_at = ?)",
                        patient.getId(), dentist.getId(), Timestamp.from(SCHEDULED_AT));
                jdbc.update("delete from appointments where patient_id = ? and professional_id = ? and scheduled_at = ?",
                        patient.getId(), dentist.getId(), Timestamp.from(SCHEDULED_AT));
                jdbc.update("delete from patients where id = ?", patient.getId());
                jdbc.update("delete from user_roles where user_id = ?", dentist.getId());
                jdbc.update("delete from users where id = ?", dentist.getId());
            });
        }
    }

    @Test
    void persistsOwnedRequestAndLinksExactlyOneConfirmedAppointment() {
        Appointment appointment = appointments.saveAndFlush(appointment(patient, SCHEDULED_AT));
        AppointmentRequest request = new AppointmentRequest(UUID.randomUUID(), patient, dentist,
                SCHEDULED_AT, AppointmentRequestStatus.PENDING, NOW, NOW);
        request.confirm(appointment, dentist, NOW.plusSeconds(1));
        appointmentRequests.saveAndFlush(request);

        AppointmentRequest recovered = appointmentRequests.findByIdAndPatient_Id(request.getId(), patient.getId())
                .orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(AppointmentRequestStatus.CONFIRMED);
        assertThat(recovered.getAppointment().getId()).isEqualTo(appointment.getId());
        assertThat(appointmentRequests.findByIdAndPatient_Id(request.getId(), UUID.randomUUID())).isEmpty();

        AppointmentRequest duplicate = new AppointmentRequest(UUID.randomUUID(), patient, dentist,
                SCHEDULED_AT.plusSeconds(3600), AppointmentRequestStatus.PENDING, NOW, NOW);
        duplicate.confirm(appointment, dentist, NOW.plusSeconds(2));
        assertThatThrownBy(() -> appointmentRequests.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void publicUnlinkedRequestAppearsInTheUnfilteredAdministrativeInbox() {
        UUID id = UUID.randomUUID();
        AppointmentRequest publicRequest = new AppointmentRequest(id, null, null, SCHEDULED_AT,
                AppointmentRequestStatus.PENDING_CLINIC, NOW, NOW, "First-time visitor", null,
                "+502 5555-0101", "visitor@example.test", "Afternoon preferred",
                UUID.randomUUID(), "a".repeat(64));
        appointmentRequests.saveAndFlush(publicRequest);

        var inbox = appointmentRequests.findAll(org.springframework.data.jpa.domain.Specification.unrestricted(),
                PageRequest.of(0, 100, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        AppointmentRequestMapper mapper = new AppointmentRequestMapper();
        AppointmentRequestResponse visible = inbox.map(mapper::toAdministrativeResponse)
                .getContent().stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow();

        assertThat(visible.status()).isEqualTo(AppointmentRequestStatus.PENDING_CLINIC);
        assertThat(visible.patient()).isNull();
        assertThat(visible.source()).isEqualTo("PUBLIC");
        assertThat(visible.contact().fullName()).isEqualTo("First-time visitor");
        assertThat(visible.contact().phone()).isEqualTo("+502 5555-0101");
        assertThat(visible.contact().email()).isEqualTo("visitor@example.test");
        assertThat(visible.contact().reason()).isEqualTo("Afternoon preferred");
        assertThat(visible.requestedAt()).isEqualTo(SCHEDULED_AT);
        assertThat(visible.requestedProfessional()).isNull();
        assertThat(visible.assignedProfessional()).isNull();
        assertThat(visible.publicRequester().fullName()).isEqualTo("First-time visitor");
        assertThat(visible.publicRequester().phone()).isEqualTo("+502 5555-0101");
        assertThat(visible.appointmentId()).isNull();

        publicRequest.assignProfessional(dentist, NOW.plusSeconds(1));
        appointmentRequests.saveAndFlush(publicRequest);
        AppointmentRequestResponse detail = mapper.toAdministrativeResponse(
                appointmentRequests.findDetailedById(id).orElseThrow());
        assertThat(detail.source()).isEqualTo("PUBLIC");
        assertThat(detail.contact().cui()).isNull();
        assertThat(detail.assignedProfessional().id()).isEqualTo(dentist.getId());
        assertThat(detail.status()).isEqualTo(AppointmentRequestStatus.PENDING_CLINIC);
        assertThat(detail.appointmentId()).isNull();
    }

    @Test
    void postgresStoresWebConversationCapabilityOnlyAsHash() {
        UUID requestId = UUID.randomUUID();
        appointmentRequests.saveAndFlush(new AppointmentRequest(requestId, null, null, SCHEDULED_AT,
                AppointmentRequestStatus.PENDING_CLINIC, NOW, NOW, "First-time visitor", null,
                "+502 5555-0101", null, null, UUID.randomUUID(), "b".repeat(64)));
        String tokenHash = "c".repeat(64);
        AppointmentPublicConversation conversation = new AppointmentPublicConversation(
                requestId, "WEB", null, null, NOW);
        conversation.issueToken(tokenHash, NOW.plusSeconds(7 * 24 * 60 * 60L), NOW);

        publicConversations.saveAndFlush(conversation);

        AppointmentPublicConversation stored = publicConversations.findByConversationTokenHash(tokenHash)
                .orElseThrow();
        assertThat(stored.getAppointmentRequestId()).isEqualTo(requestId);
        assertThat(stored.getChannel()).isEqualTo("WEB");
        assertThat(stored.getConversationTokenHash()).isEqualTo(tokenHash);
        assertThat(stored.getConversationExpiresAt()).isEqualTo(NOW.plusSeconds(7 * 24 * 60 * 60L));
    }

    @Test
    void postgresPersistsIdempotentPublicAndReceptionMessagesAndCursorOrderingFields() {
        AppointmentRequest request = new AppointmentRequest(UUID.randomUUID(), null, null, SCHEDULED_AT,
                AppointmentRequestStatus.PENDING_CLINIC, NOW, NOW, "Public visitor", null,
                "+502 5555-0101", null, null, UUID.randomUUID(), "a".repeat(64));
        appointmentRequests.saveAndFlush(request);
        UUID key = UUID.randomUUID();
        var message = new AppointmentRequestMessage(UUID.randomUUID(), request.getId(), "PATIENT", "FREE_TEXT",
                "Please call after 3 pm", NOW.plusSeconds(2), key, "b".repeat(64));
        var older = new AppointmentRequestMessage(UUID.randomUUID(), request.getId(), "RECEPTION", "FREE_TEXT",
                "We will call", NOW.plusSeconds(1), UUID.randomUUID(), "d".repeat(64));
        messages.saveAllAndFlush(List.of(message, older));

        assertThat(messages.findByAppointmentRequestIdAndSenderAndIdempotencyKey(request.getId(), "PATIENT", key))
                .get().extracting(AppointmentRequestMessage::getIdempotencyPayloadHash).isEqualTo("b".repeat(64));
        assertThat(messages.findLatest(request.getId(), PageRequest.of(0, 1)))
                .extracting(AppointmentRequestMessage::getId).containsExactly(message.getId());
        assertThat(messages.findOlderThan(request.getId(), message.getCreatedAt(), message.getId(),
                PageRequest.of(0, 1))).extracting(AppointmentRequestMessage::getId).containsExactly(older.getId());
    }

    @Test
    void postgresOutboxAllowsWipingEncryptedPayloadAfterProviderAcceptance() {
        AppointmentRequest request = new AppointmentRequest(UUID.randomUUID(), null, null, SCHEDULED_AT,
                AppointmentRequestStatus.PENDING_CLINIC, NOW, NOW, "Public visitor", null,
                "+502 5555-0101", null, null, UUID.randomUUID(), "c".repeat(64));
        appointmentRequests.saveAndFlush(request);
        UUID eventId = UUID.randomUUID();
        AppointmentNotificationOutboxEvent event = new AppointmentNotificationOutboxEvent(eventId,
                request.getId(), "OTP", "SMS", "encrypted-recipient", "encrypted-payload", eventId,
                eventId.toString(), NOW);
        notificationOutbox.saveAndFlush(event);
        event.claim(NOW);
        event.markSent(NOW.plusSeconds(1));
        notificationOutbox.saveAndFlush(event);

        AppointmentNotificationOutboxEvent recovered = notificationOutbox.findById(eventId).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(AppointmentNotificationStatus.SENT);
        assertThat(recovered.getRecipientCiphertext()).isNull();
        assertThat(recovered.getPayloadCiphertext()).isNull();
    }

    @Test
    void publicRequestManuallyLinkedAfterVerificationStillHasPublicSourceInAdministrativeListAndDetail() {
        AppointmentRequest linkedPublic = new AppointmentRequest(UUID.randomUUID(), patient, dentist,
                SCHEDULED_AT, AppointmentRequestStatus.PENDING_PATIENT, NOW, NOW, "Public name", patient.getDpi(),
                "+502 5555-0101", "public@example.test", "Afternoon", UUID.randomUUID(), "d".repeat(64));
        linkedPublic.propose(dentist, SCHEDULED_AT.plusSeconds(3600), dentist, NOW.plusSeconds(30));
        linkedPublic.verifyRequesterIdentity(dentist, "DOCUMENT_REVIEW", NOW.plusSeconds(15));
        appointmentRequests.saveAndFlush(linkedPublic);
        AppointmentRequestMapper mapper = new AppointmentRequestMapper();

        var page = appointmentRequests.findAll(org.springframework.data.jpa.domain.Specification.unrestricted(),
                PageRequest.of(0, 100, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        AppointmentRequestResponse fromList = page.map(mapper::toAdministrativeResponse).getContent().stream()
                .filter(value -> value.id().equals(linkedPublic.getId())).findFirst().orElseThrow();
        AppointmentRequestResponse fromDetail = mapper.toAdministrativeResponse(
                appointmentRequests.findDetailedById(linkedPublic.getId()).orElseThrow());

        assertThat(fromList.patient().id()).isEqualTo(patient.getId());
        assertThat(fromList.source()).isEqualTo("PUBLIC");
        assertThat(fromList.contact().fullName()).isEqualTo("Public name");
        assertThat(fromList.requestedProfessional().id()).isEqualTo(dentist.getId());
        assertThat(fromList.assignedProfessional()).isNull();
        assertThat(fromList.status()).isEqualTo(AppointmentRequestStatus.PENDING_PATIENT);
        assertThat(fromDetail.source()).isEqualTo("PUBLIC");
        assertThat(fromDetail.contact().cui()).isEqualTo(patient.getDpi());
        assertThat(fromDetail.status()).isEqualTo(AppointmentRequestStatus.PENDING_PATIENT);
        assertThat(fromDetail.identityVerification().verifiedByUserId()).isEqualTo(dentist.getId());
        assertThat(fromDetail.identityVerification().method()).isEqualTo("DOCUMENT_REVIEW");
    }

    @Test
    void postgresPreventsDuplicateActivePublicRequestForSamePayloadWithoutUsingCuiAsIdentity() {
        appointmentRequests.saveAndFlush(new AppointmentRequest(UUID.randomUUID(), null, dentist,
                SCHEDULED_AT, AppointmentRequestStatus.PENDING_CLINIC, NOW, NOW, "Appointment Patient",
                patient.getDpi(), "55550000", null, null, UUID.randomUUID(), "b".repeat(64)));

        AppointmentRequest duplicate = new AppointmentRequest(UUID.randomUUID(), null, dentist,
                SCHEDULED_AT, AppointmentRequestStatus.PENDING_CLINIC, NOW, NOW, "Appointment Patient",
                patient.getDpi(), "55550000", null, null, UUID.randomUUID(), "b".repeat(64));

        assertThatThrownBy(() -> appointmentRequests.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void waitingRoomAllowsOnlyOneOperationalEntryPerAppointment() {
        Appointment appointment = appointments.saveAndFlush(appointment(patient, SCHEDULED_AT));
        waitingRoom.saveAndFlush(new WaitingRoomEntry(UUID.randomUUID(), appointment, dentist, NOW));

        assertThat(waitingRoom.findByAppointment_Id(appointment.getId())).isPresent();
        assertThatThrownBy(() -> waitingRoom.saveAndFlush(
                new WaitingRoomEntry(UUID.randomUUID(), appointment, dentist, NOW.plusSeconds(1))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void cancellingAppointmentUpdatesStatusAndTimestampWithoutDeletingRow() {
        UUID id = UUID.randomUUID();
        Appointment appointment = appointments.saveAndFlush(new Appointment(id, patient, dentist, SCHEDULED_AT,
                AppointmentStatus.SCHEDULED, NOW, NOW));
        long countBefore = appointments.count();

        Instant cancelledAt = NOW.plusSeconds(300);
        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setUpdatedAt(cancelledAt);
        appointments.saveAndFlush(appointment);

        assertThat(appointments.count()).isEqualTo(countBefore);

        Appointment recovered = appointments.findById(id).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(recovered.getCreatedAt()).isEqualTo(NOW);
        assertThat(recovered.getUpdatedAt()).isEqualTo(cancelledAt);

        Optional<Appointment> owned = appointments.findByIdAndPatient_Id(id, patient.getId());
        assertThat(owned).isPresent();
        assertThat(owned.get().getStatus()).isEqualTo(AppointmentStatus.CANCELLED);

        // Slot is freed for another scheduled appointment with the same dentist
        Patient otherPatient = patients.save(patient("2000000000005"));
        Appointment newAppointment = appointments.saveAndFlush(new Appointment(
                UUID.randomUUID(), otherPatient, dentist, SCHEDULED_AT,
                AppointmentStatus.SCHEDULED, cancelledAt, cancelledAt));
        assertThat(newAppointment.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
    }

    @Test
    void rejectsUnknownPatientForeignKey() {
        UUID missingPatientId = UUID.randomUUID();

        assertThatThrownBy(() -> jdbc.update("""
                        INSERT INTO appointments
                            (id, patient_id, professional_id, scheduled_at, status, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """, UUID.randomUUID(), missingPatientId, dentist.getId(), Timestamp.from(SCHEDULED_AT),
                AppointmentStatus.SCHEDULED.name(), Timestamp.from(NOW), Timestamp.from(NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void listsOnlyRequestedPatientsAppointmentsInDeterministicOrder() {
        Patient otherPatient = patients.save(patient("2000000000003"));
        Appointment older = appointment(patient, Instant.parse("2026-10-01T10:00:00Z"));
        Appointment newer = appointment(patient, Instant.parse("2026-10-02T10:00:00Z"));
        appointments.saveAllAndFlush(Set.of(older, newer, appointment(otherPatient,
                Instant.parse("2026-10-03T10:00:00Z"))));

        var result = appointments.findByPatient_Id(patient.getId(), PageRequest.of(0, 10,
                Sort.by(Sort.Order.desc("scheduledAt"), Sort.Order.desc("id"))));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).extracting(Appointment::getId)
                .containsExactly(newer.getId(), older.getId());
        assertThat(result.getContent()).allMatch(value -> value.getPatient().getId().equals(patient.getId()));
    }

    @Test
    void detailQueryDoesNotReturnAnotherPatientsAppointment() {
        Patient otherPatient = patients.save(patient("2000000000003"));
        Appointment foreignAppointment = appointments.saveAndFlush(appointment(otherPatient, SCHEDULED_AT));

        Optional<Appointment> result = appointments.findByIdAndPatient_Id(
                foreignAppointment.getId(), patient.getId());

        assertThat(result).isEmpty();
        assertThat(appointments.findByIdAndPatient_Id(foreignAppointment.getId(), otherPatient.getId()))
                .isPresent();
    }

    @Test
    void lockedOwnershipQueryDoesNotReturnAnotherPatientsAppointment() {
        Patient otherPatient = patients.save(patient("2000000000007"));
        Appointment foreignAppointment = appointments.saveAndFlush(appointment(otherPatient, SCHEDULED_AT));

        Optional<Appointment> result = appointments.findByIdAndPatient_IdForUpdate(
                foreignAppointment.getId(), patient.getId());

        assertThat(result).isEmpty();
        assertThat(appointments.findByIdAndPatient_IdForUpdate(
                foreignAppointment.getId(), otherPatient.getId())).isPresent();
    }

    @Test
    void postgresRejectsTwoScheduledAppointmentsForSameProfessionalAndInstant() {
        appointments.saveAndFlush(appointment(patient, SCHEDULED_AT));

        assertThatThrownBy(() -> appointments.saveAndFlush(appointment(patient, SCHEDULED_AT)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void cancelledAppointmentDoesNotBlockScheduledAppointmentAtSameInstant() {
        Appointment cancelled = new Appointment(UUID.randomUUID(), patient, dentist, SCHEDULED_AT,
                AppointmentStatus.CANCELLED, NOW, NOW);
        Appointment scheduled = appointment(patient, SCHEDULED_AT);

        appointments.saveAllAndFlush(Set.of(cancelled, scheduled));

        assertThat(appointments.findById(cancelled.getId())).isPresent();
        assertThat(appointments.findById(scheduled.getId())).isPresent();
    }

    @Test
    void differentProfessionalsCanBeScheduledAtSameInstant() {
        Role dentistRole = roles.findByCode("DENTIST").orElseThrow();
        User otherDentist = users.save(user(dentistRole, "2000000000004"));

        appointments.saveAllAndFlush(Set.of(
                appointment(patient, SCHEDULED_AT),
                new Appointment(UUID.randomUUID(), patient, otherDentist, SCHEDULED_AT,
                        AppointmentStatus.SCHEDULED, NOW, NOW)));

        assertThat(appointments.count()).isEqualTo(2);
    }

    @Test
    void activeDentistCatalogExcludesInactiveUsersAndInactiveRoles() {
        Role dentistRole = roles.findByCode("DENTIST").orElseThrow();
        assertThat(users.findActiveDentists()).extracting(User::getId)
                .containsExactly(dentist.getId());

        User inactiveUser = user(dentistRole, "2000000000004");
        inactiveUser.setStatus(UserStatus.INACTIVE);
        users.saveAndFlush(inactiveUser);

        assertThat(users.findActiveDentists()).extracting(User::getId)
                .containsExactly(dentist.getId());

        dentistRole.setActive(false);
        roles.saveAndFlush(dentistRole);
        assertThat(users.findActiveDentists()).isEmpty();
    }

    private Patient patient() {
        return patient(String.format("%013d", PATIENT_DPI_SEQUENCE.getAndIncrement()));
    }

    private Patient patient(String dpi) {
        Patient result = new Patient();
        result.setId(UUID.randomUUID());
        result.setCode("PAT-" + UUID.randomUUID().toString().substring(0, 8));
        result.setName("Appointment Patient");
        result.setDpi(dpi);
        result.setBirthDate(LocalDate.of(1990, 1, 1));
        result.setGender(Gender.OTHER);
        result.setPhone("55550000");
        result.setCreatedAt(NOW);
        result.setUpdatedAt(NOW);
        return result;
    }

    private Appointment appointment(Patient owner, Instant scheduledAt) {
        return new Appointment(UUID.randomUUID(), owner, dentist, scheduledAt,
                AppointmentStatus.SCHEDULED, NOW, NOW);
    }

    private User user(Role role) {
        return user(role, String.format("%013d", USER_CUI_SEQUENCE.getAndIncrement()));
    }

    private User user(Role role, String cui) {
        User result = new User(UUID.randomUUID(), "dentist-" + UUID.randomUUID(), "Dentist",
                "dentist-" + UUID.randomUUID() + "@example.test", cui, "hash",
                UserStatus.ACTIVE, NOW, NOW);
        result.setRoles(Set.of(role));
        return result;
    }
}
