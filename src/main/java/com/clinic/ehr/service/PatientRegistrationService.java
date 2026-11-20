package com.clinic.ehr.service;

import com.clinic.ehr.dto.RegisterRequest;
import com.clinic.ehr.exception.InvalidBookingException;
import com.clinic.ehr.model.Patient;
import com.clinic.ehr.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Public self-registration. Always produces a PATIENT. */
@Service
public class PatientRegistrationService extends AbstractAccountCreator<RegisterRequest, Patient> {

    private final Clock clock;

    public PatientRegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                      AuditService auditService) {
        this(userRepository, passwordEncoder, auditService, Clock.systemDefaultZone());
    }

    @Autowired
    public PatientRegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                      AuditService auditService, Clock clock) {
        super(userRepository, passwordEncoder, auditService);
        this.clock = clock;
    }

    @Override
    protected void checkExtraRules(RegisterRequest request) {
        LocalDate maxAllowedDob = LocalDate.now(clock).minusYears(18);
        if (request.dateOfBirth().isAfter(maxAllowedDob)) {
            throw new InvalidBookingException("Patient must be at least 18 years old to self-register");
        }
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