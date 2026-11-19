package com.clinic.ehr.service;

import com.clinic.ehr.dto.CreateDoctorRequest;
import com.clinic.ehr.exception.DuplicateResourceException;
import com.clinic.ehr.model.Doctor;
import com.clinic.ehr.repository.DoctorRepository;
import com.clinic.ehr.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Doctor accounts are created by an admin only (see AdminController). */
@Service
public class DoctorAccountService extends AbstractAccountCreator<CreateDoctorRequest, Doctor> {

    private final DoctorRepository doctorRepository;

    public DoctorAccountService(UserRepository userRepository, DoctorRepository doctorRepository,
                                PasswordEncoder passwordEncoder, AuditService auditService) {
        super(userRepository, passwordEncoder, auditService);
        this.doctorRepository = doctorRepository;
    }

    @Override
    protected void checkExtraRules(CreateDoctorRequest request) {
        if (doctorRepository.existsByLicenseNumber(request.licenseNumber().trim())) {
            throw new DuplicateResourceException("A doctor with this license number already exists");
        }
    }

    @Override
    protected Doctor build(CreateDoctorRequest request, String normalizedEmail, String passwordHash) {
        Doctor doctor = new Doctor(normalizedEmail, passwordHash, request.fullName().trim(), request.phone(),
                request.specialization().trim(), request.licenseNumber().trim());
        doctor.setQualification(request.qualification());
        if (request.slotMinutes() != null) {
            doctor.setSlotMinutes(request.slotMinutes());
        }
        return doctor;
    }

    @Override
    protected String auditDetails(Doctor doctor) {
        return "Created DOCTOR account " + doctor.getEmail() + " (" + doctor.getSpecialization() + ")";
    }
}
