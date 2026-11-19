package com.clinic.ehr.service;

import com.clinic.ehr.dto.RegisterRequest;
import com.clinic.ehr.model.Patient;
import com.clinic.ehr.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Public self-registration. Always produces a PATIENT. */
@Service
public class PatientRegistrationService extends AbstractAccountCreator<RegisterRequest, Patient> {

    public PatientRegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                      AuditService auditService) {
        super(userRepository, passwordEncoder, auditService);
    }

    @Override
    protected Patient build(RegisterRequest request, String normalizedEmail, String passwordHash) {
        Patient patient = new Patient(normalizedEmail, passwordHash, request.fullName().trim(), request.phone(),
                request.dateOfBirth(), request.gender());
        patient.setAddress(request.address());
        patient.setBloodGroup(request.bloodGroup());
        patient.setEmergencyContact(request.emergencyContact());
        return patient;
    }
}
