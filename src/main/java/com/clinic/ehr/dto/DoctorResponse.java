package com.clinic.ehr.dto;

import com.clinic.ehr.model.Doctor;

/** Public doctor profile. Contains no contact details. */
public record DoctorResponse(Long id, String fullName, String displayName, String specialization,
                             String qualification, int slotMinutes) {

    public static DoctorResponse from(Doctor doctor) {
        return new DoctorResponse(doctor.getId(), doctor.getFullName(), doctor.getDisplayName(),
                doctor.getSpecialization(), doctor.getQualification(), doctor.getSlotMinutes());
    }
}
