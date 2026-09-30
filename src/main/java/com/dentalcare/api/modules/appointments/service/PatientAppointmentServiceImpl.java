package com.dentalcare.api.modules.appointments.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.appointments.dto.response.AppointmentProfessionalResponse;
import com.dentalcare.api.modules.appointments.dto.response.PatientAppointmentResponse;
import com.dentalcare.api.modules.appointments.mapper.AppointmentMapper;
import com.dentalcare.api.modules.appointments.model.Appointment;
import com.dentalcare.api.modules.appointments.repository.AppointmentRepository;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PatientAppointmentServiceImpl implements PatientAppointmentService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort APPOINTMENT_ORDER = Sort.by(
            Sort.Order.desc("scheduledAt"), Sort.Order.desc("id"));

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final AppointmentMapper appointmentMapper;
    private final AppointmentService appointmentService;
    private final UserRepository userRepository;

    public PatientAppointmentServiceImpl(AppointmentRepository appointmentRepository,
                                         PatientRepository patientRepository,
                                         AppointmentMapper appointmentMapper,
                                         AppointmentService appointmentService,
                                         UserRepository userRepository) {
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.appointmentMapper = appointmentMapper;
        this.appointmentService = appointmentService;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PatientAppointmentResponse> findCurrentPatientAppointments(
            UUID authenticatedUserId, int page, int size) {
        if (page < 0) throw new BadRequestException("Page must be at least 0");
        if (size < 1) throw new BadRequestException("Size must be at least 1");
        Patient patient = findPatientByAuthenticatedUser(authenticatedUserId);

        PageRequest pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), APPOINTMENT_ORDER);
        return appointmentRepository.findByPatient_Id(patient.getId(), pageable)
                .map(appointmentMapper::toPatientResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PatientAppointmentResponse findCurrentPatientAppointment(
            UUID authenticatedUserId, UUID appointmentId) {
        if (appointmentId == null) throw new BadRequestException("Appointment id is required");
        Patient patient = findPatientByAuthenticatedUser(authenticatedUserId);
        return appointmentRepository.findByIdAndPatient_Id(appointmentId, patient.getId())
                .map(appointmentMapper::toPatientResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
    }

    @Override
    @Transactional
    public PatientAppointmentResponse createCurrentPatientAppointment(
            UUID authenticatedUserId, UUID professionalId, Instant scheduledAt) {
        Patient patient = findPatientByAuthenticatedUser(authenticatedUserId);
        return appointmentMapper.toPatientResponse(
                appointmentService.create(patient.getId(), professionalId, scheduledAt));
    }

    @Override
    @Transactional
    public PatientAppointmentResponse cancelCurrentPatientAppointment(
            UUID authenticatedUserId, UUID appointmentId) {
        if (appointmentId == null) throw new BadRequestException("Appointment id is required");
        Patient patient = findPatientByAuthenticatedUser(authenticatedUserId);
        Appointment appointment = appointmentRepository.findByIdAndPatient_Id(appointmentId, patient.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
        Appointment cancelledAppointment = appointmentService.cancel(appointment);
        return appointmentMapper.toPatientResponse(cancelledAppointment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentProfessionalResponse> findAvailableProfessionals() {
        return userRepository.findActiveDentists().stream()
                .map(appointmentMapper::toProfessionalResponse)
                .toList();
    }

    private Patient findPatientByAuthenticatedUser(UUID authenticatedUserId) {
        if (authenticatedUserId == null) throw new BadRequestException("Authenticated user id is required");
        return patientRepository.findByUser_Id(authenticatedUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
    }
}
