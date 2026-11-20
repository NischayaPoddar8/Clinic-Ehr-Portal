package com.clinic.ehr.dto;

import com.clinic.ehr.model.Appointment;
import com.clinic.ehr.model.enums.AppointmentStatus;
import java.time.LocalDateTime;

public record AppointmentResponse(Long id, Long doctorId, String doctorName, String specialization,
                                  Long patientId, String patientName, LocalDateTime startTime,
                                  LocalDateTime endTime, AppointmentStatus status, String reason,
                                  Long medicalRecordId) {

    /** Needs patient and doctor to be loaded (call inside a transaction or after an entity-graph query). */
    public static AppointmentResponse from(Appointment a) {
        return from(a, null);
    }

    public static AppointmentResponse from(Appointment a, Long medicalRecordId) {
        return new AppointmentResponse(a.getId(), a.getDoctor().getId(), a.getDoctor().getDisplayName(),
                a.getDoctor().getSpecialization(), a.getPatient().getId(), a.getPatient().getFullName(),
                a.getStartTime(), a.getEndTime(), a.getStatus(), a.getReason(), medicalRecordId);
    }
}