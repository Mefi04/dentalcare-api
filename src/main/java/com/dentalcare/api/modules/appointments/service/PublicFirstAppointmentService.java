package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.modules.appointments.dto.request.FirstAppointmentIntakeRequest;
import com.dentalcare.api.modules.appointments.dto.response.FirstAppointmentReceipt;
import com.dentalcare.api.modules.appointments.model.AppointmentRequest;
import com.dentalcare.api.modules.appointments.model.AppointmentRequestStatus;
import com.dentalcare.api.modules.appointments.repository.AppointmentRequestRepository;
import com.dentalcare.api.modules.audit.service.AuditActions;
import com.dentalcare.api.modules.audit.service.AuditService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class PublicFirstAppointmentService {
    private static final String MESSAGE = "¡Gracias por solicitar tu primera cita en DentalCare! "
            + "Hemos recibido tus datos. Nuestro equipo de recepción se comunicará contigo al número "
            + "proporcionado para confirmar la fecha y hora de tu atención. La cita todavía no está confirmada.";
    private final AppointmentRequestRepository requests;
    private final GeneralDentistryAvailabilityService availability;
    private final AuditService audit;
    private final ObjectMapper json;
    private final Clock clock;

    public PublicFirstAppointmentService(AppointmentRequestRepository requests,
            GeneralDentistryAvailabilityService availability, AuditService audit,
            ObjectMapper json, Clock clock) {
        this.requests = requests;
        this.availability = availability;
        this.audit = audit;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public FirstAppointmentReceipt submit(FirstAppointmentIntakeRequest input, UUID idempotencyKey) {
        if (idempotencyKey == null) throw new BadRequestException("Idempotency-Key is required");
        if (!input.privacyAccepted()) throw new BadRequestException("Privacy consent is required");
        LocalDate clinicDate = LocalDate.now(clock.withZone(GeneralDentistryAvailabilityService.CLINIC_ZONE));
        if (input.birthDate() == null || input.birthDate().isAfter(clinicDate)) {
            throw new BadRequestException("Valid birth date is required");
        }
        int age = Period.between(input.birthDate(), clinicDate).getYears();
        if (age >= 18 && (input.cui() == null || input.cui().isBlank())
                && (input.alternativeId() == null || input.alternativeId().isBlank())) {
            throw new BadRequestException("DPI/CUI or alternative identification is required for adults");
        }
        if (age < 18 && (input.guardianName() == null || input.guardianName().isBlank()
                || input.guardianPhone() == null || input.guardianPhone().isBlank())) {
            throw new BadRequestException("A legal guardian and contact number are required for minors");
        }
        if (input.requestedAt() == null || !input.requestedAt().isAfter(clock.instant())) {
            throw new BadRequestException("Preferred time must be in the future");
        }
        NonClinicalSchedulingText.validate(input.logisticsNote());
        if (input.requestedAt().atZone(GeneralDentistryAvailabilityService.CLINIC_ZONE).getMinute() % 30 != 0
                || input.requestedAt().atZone(GeneralDentistryAvailabilityService.CLINIC_ZONE).getSecond() != 0) {
            throw new BadRequestException("Preferred time must align with a published availability slot");
        }
        String hash = hash(input);
        var previous = requests.findByIdempotencyKey(idempotencyKey);
        if (previous.isPresent()) {
            if (!hash.equals(previous.get().getIdempotencyPayloadHash())) {
                throw new ConflictException("Idempotency key was already used with different data");
            }
            return new FirstAppointmentReceipt(previous.get().getId(), false, MESSAGE);
        }
        var day = input.requestedAt().atZone(GeneralDentistryAvailabilityService.CLINIC_ZONE).toLocalDate();
        boolean offered = availability.forDate(day).slots().stream()
                .anyMatch(slot -> slot.startsAt().equals(input.requestedAt())
                        && (slot.status().equals("AVAILABLE") || slot.status().equals("REQUESTED")));
        if (!offered) throw new ConflictException("Preferred time is not available for general dentistry");
        var now = clock.instant();
        var entity = new AppointmentRequest(UUID.randomUUID(), null, null, input.requestedAt(),
                AppointmentRequestStatus.PENDING_CLINIC, now, now, clean(input.fullName()),
                clean(input.cui()), clean(input.phone()), clean(input.email()),
                clean(input.logisticsNote()), idempotencyKey, hash);
        entity.setAdministrativeIntake(input.birthDate(), input.gender(), clean(input.alternativeId()),
                clean(input.guardianName()), clean(input.guardianRelationship()), clean(input.guardianPhone()),
                clean(input.department()), clean(input.municipality()), clean(input.address()),
                clean(input.emergencyName()), clean(input.emergencyPhone()), clean(input.nit()),
                clean(input.billingName()), clean(input.billingAddress()),
                clean(input.privacyNoticeVersion()), now);
        requests.saveAndFlush(entity);
        audit.success(AuditActions.APPOINTMENT_REQUEST_CREATED, "APPOINTMENTS",
                "PublicAppointmentRequest", entity.getId(), null);
        return new FirstAppointmentReceipt(entity.getId(), false, MESSAGE);
    }

    private String hash(FirstAppointmentIntakeRequest input) {
        try {
            byte[] payload = json.writeValueAsBytes(input);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to hash appointment request", exception);
        }
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
