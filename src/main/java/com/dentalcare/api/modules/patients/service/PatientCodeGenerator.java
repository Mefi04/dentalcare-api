package com.dentalcare.api.modules.patients.service;

import com.dentalcare.api.modules.patients.repository.PatientRepository;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class PatientCodeGenerator {

    private final PatientRepository patientRepository;

    public PatientCodeGenerator(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    public String nextCode() {
        return String.format(Locale.ROOT, "PAC-%05d", patientRepository.nextPatientCodeSequence());
    }
}
