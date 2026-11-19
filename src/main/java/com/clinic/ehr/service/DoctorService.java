package com.clinic.ehr.service;

import com.clinic.ehr.dto.DoctorResponse;
import com.clinic.ehr.dto.UpdateDoctorProfileRequest;
import com.clinic.ehr.exception.ResourceNotFoundException;
import com.clinic.ehr.model.Doctor;
import com.clinic.ehr.model.enums.AuditAction;
import com.clinic.ehr.model.enums.AuditEntityType;
import com.clinic.ehr.repository.DoctorRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final AuditService auditService;

    public DoctorService(DoctorRepository doctorRepository, AuditService auditService) {
        this.doctorRepository = doctorRepository;
        this.auditService = auditService;
    }

    /** Active doctors, optionally filtered by specialization and/or name (both case-insensitive "contains"). */
    @Transactional(readOnly = true)
    public List<DoctorResponse> search(String specialization, String name) {
        return doctorRepository.searchActive(blankToEmpty(specialization), blankToEmpty(name))
                .stream().map(DoctorResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public DoctorResponse getActiveDoctor(Long doctorId) {
        return doctorRepository.findById(doctorId)
                .filter(Doctor::isActive)
                .map(DoctorResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + doctorId));
    }

    @Transactional
    public DoctorResponse updateProfile(Long doctorId, UpdateDoctorProfileRequest request) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + doctorId));
        doctor.setFullName(request.fullName().trim());
        doctor.setPhone(request.phone());
        doctor.setSpecialization(request.specialization().trim());
        doctor.setQualification(request.qualification());
        doctor.setSlotMinutes(request.slotMinutes());
        auditService.record(doctor, AuditAction.UPDATE, AuditEntityType.USER, doctor.getId(),
                "Doctor profile updated (" + doctor.getSpecialization() + ", " + doctor.getSlotMinutes() + " min slots)");
        return DoctorResponse.from(doctor);
    }

    private static String blankToEmpty(String text) {
        return text == null ? "" : text.trim();
    }
}
