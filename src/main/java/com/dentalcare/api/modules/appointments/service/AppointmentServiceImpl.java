package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.appointments.model.Appointment;
import com.dentalcare.api.modules.appointments.model.AppointmentStatus;
import com.dentalcare.api.modules.appointments.repository.AppointmentRepository;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
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
    private final Clock clock;

    public AppointmentServiceImpl(AppointmentRepository appointmentRepository,
                                  PatientRepository patientRepository,
                                  UserRepository userRepository,
                                  Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
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
                professionalId, scheduledAt, AppointmentStatus.SCHEDULED)) {
            throw new ConflictException("Appointment time is not available");
        }

        Instant now = clock.instant();
        Appointment appointment = new Appointment(UUID.randomUUID(), patient, professional, scheduledAt,
                AppointmentStatus.SCHEDULED, now, now);
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

    private void validateDentist(User professional) {
        boolean activeDentist = professional.getStatus() == UserStatus.ACTIVE
                && professional.getRoles().stream()
                .anyMatch(role -> role.isActive() && DENTIST_ROLE.equals(role.getCode()));
        if (!activeDentist) {
            throw new ConflictException("Professional is not an active dentist");
        }
    }
}
