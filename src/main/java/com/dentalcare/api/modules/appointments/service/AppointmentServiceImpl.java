package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.appointments.model.Appointment;
import com.dentalcare.api.modules.appointments.model.AppointmentStatus;
import com.dentalcare.api.modules.appointments.repository.AppointmentRepository;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.notifications.model.NotificationEventType;
import com.dentalcare.api.modules.notifications.service.PatientNotificationPublisher;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class AppointmentServiceImpl implements AppointmentService {
    private static final String DENTIST_ROLE = "DENTIST";

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final PatientNotificationPublisher notificationPublisher;
    private final Clock clock;

    public AppointmentServiceImpl(AppointmentRepository appointmentRepository,
                                  PatientRepository patientRepository,
                                  UserRepository userRepository,
                                  PatientNotificationPublisher notificationPublisher,
                                  Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
        this.notificationPublisher = notificationPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Appointment create(UUID patientId, UUID professionalId, Instant scheduledAt) {
        if (patientId == null) throw new BadRequestException("Patient id is required");
        if (professionalId == null) throw new BadRequestException("Professional id is required");
        if (scheduledAt == null) throw new BadRequestException("Appointment date and time are required");
        if (!scheduledAt.isAfter(clock.instant())) {
            throw new BadRequestException("Appointment date and time must be in the future");
        }

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
        User professional = userRepository.findWithRolesById(professionalId)
                .orElseThrow(() -> new ResourceNotFoundException("Professional not found"));
        validateDentist(professional);
        if (appointmentRepository.existsByProfessional_IdAndScheduledAtAndStatus(
                professionalId, scheduledAt, AppointmentStatus.SCHEDULED)
                || appointmentRepository.existsByProfessional_IdAndStatusAndScheduledAtLessThanAndEndsAtGreaterThan(
                professionalId, AppointmentStatus.SCHEDULED, scheduledAt.plusSeconds(1800), scheduledAt)) {
            throw new ConflictException("Appointment time is not available");
        }

        Instant now = clock.instant();
        Appointment appointment = new Appointment(UUID.randomUUID(), patient, professional, scheduledAt,
                AppointmentStatus.SCHEDULED, now, now);
        try {
            Appointment saved = appointmentRepository.saveAndFlush(appointment);
            notificationPublisher.publish(patientId, NotificationEventType.APPOINTMENT_SCHEDULED,
                    "Cita programada", "Tu cita fue programada. Consulta Mis citas para ver los detalles.");
            return saved;
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Appointment time is not available");
        }
    }

    @Override
    @Transactional
    public Appointment createPublic(String fullName, String phone, UUID professionalId, Instant scheduledAt) {
        if (fullName == null || fullName.isBlank() || phone == null || phone.isBlank()) {
            throw new BadRequestException("Public appointment contact is required");
        }
        if (professionalId == null) throw new BadRequestException("Professional id is required");
        if (scheduledAt == null || !scheduledAt.isAfter(clock.instant())) {
            throw new BadRequestException("Appointment date and time must be in the future");
        }
        User professional = userRepository.findWithRolesById(professionalId)
                .orElseThrow(() -> new ResourceNotFoundException("Professional not found"));
        validateDentist(professional);
        if (appointmentRepository.existsByProfessional_IdAndScheduledAtAndStatus(
                professionalId, scheduledAt, AppointmentStatus.SCHEDULED)
                || appointmentRepository.existsByProfessional_IdAndStatusAndScheduledAtLessThanAndEndsAtGreaterThan(
                professionalId, AppointmentStatus.SCHEDULED, scheduledAt.plusSeconds(1800), scheduledAt)) {
            throw new ConflictException("Appointment time is not available");
        }
        Appointment appointment = Appointment.forPublicRequest(UUID.randomUUID(), fullName, phone,
                professional, scheduledAt, clock.instant());
        try {
            return appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Appointment time is not available");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Appointment findById(UUID id) {
        if (id == null) throw new BadRequestException("Appointment id is required");
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
    }

    @Override
    @Transactional
    public Appointment cancel(Appointment appointment) {
        if (appointment == null) throw new BadRequestException("Appointment is required");
        if (appointment.getStatus() != AppointmentStatus.SCHEDULED) {
            throw new ConflictException("Only scheduled appointments can be cancelled");
        }
        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setUpdatedAt(clock.instant());
        Appointment saved = appointmentRepository.saveAndFlush(appointment);
        if (appointment.getPatient() != null) {
            notificationPublisher.publish(appointment.getPatient().getId(), NotificationEventType.APPOINTMENT_CANCELLED,
                    "Cita cancelada", "Tu cita fue cancelada. Consulta Mis citas para ver los detalles.");
        }
        return saved;
    }

    @Override
    @Transactional
    public Appointment reschedule(Appointment appointment, Instant scheduledAt) {
        if (appointment == null) throw new BadRequestException("Appointment is required");
        if (scheduledAt == null) {
            throw new BadRequestException("Appointment date and time are required");
        }
        if (!scheduledAt.isAfter(clock.instant())) {
            throw new BadRequestException("Appointment date and time must be in the future");
        }
        if (appointment.getStatus() != AppointmentStatus.SCHEDULED) {
            throw new ConflictException("Only scheduled appointments can be rescheduled");
        }
        if (scheduledAt.equals(appointment.getScheduledAt())) {
            return appointment;
        }
        if (appointmentRepository.existsByProfessional_IdAndScheduledAtAndStatusAndIdNot(
                appointment.getProfessional().getId(), scheduledAt,
                AppointmentStatus.SCHEDULED, appointment.getId())
                || appointmentRepository.existsByProfessional_IdAndStatusAndScheduledAtLessThanAndEndsAtGreaterThanAndIdNot(
                appointment.getProfessional().getId(), AppointmentStatus.SCHEDULED,
                scheduledAt.plusSeconds(appointment.getDurationMinutes() * 60L), scheduledAt, appointment.getId())) {
            throw new ConflictException("Appointment time is not available");
        }

        appointment.setScheduledAt(scheduledAt);
        appointment.setUpdatedAt(clock.instant());
        try {
            Appointment saved = appointmentRepository.saveAndFlush(appointment);
            if (appointment.getPatient() != null) {
                notificationPublisher.publish(appointment.getPatient().getId(), NotificationEventType.APPOINTMENT_RESCHEDULED,
                        "Cita reprogramada", "Tu cita cambió de fecha u hora. Consulta Mis citas para ver los detalles.");
            }
            return saved;
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Appointment time is not available");
        }
    }

    private void validateDentist(User professional) {
        boolean activeDentist = professional.getStatus() == UserStatus.ACTIVE
                && professional.getRoles().stream()
                .anyMatch(role -> role.isActive() && DENTIST_ROLE.equals(role.getCode()));
        if (!activeDentist) {
            throw new ConflictException("Professional is not an active dentist");
        }
    }
}
