package com.clinic.ehr.dto;

import com.clinic.ehr.model.Appointment;
import com.clinic.ehr.model.Doctor;
import com.clinic.ehr.model.MedicalRecord;
import com.clinic.ehr.model.Patient;
import java.time.LocalDateTime;
import java.util.List;

public record MedicalRecordResponse(
        Long id,
        Long appointmentId,
        Long patientId,
        String patientName,
        Long doctorId,
        String doctorName,
        String specialization,
        LocalDateTime appointmentStartTime,
        LocalDateTime appointmentEndTime,
        String diagnosis,
        String notes,
        List<PrescriptionResponse> prescriptions,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static MedicalRecordResponse from(MedicalRecord r) {
        Appointment a = r.getAppointment();
        Doctor d = a.getDoctor();
        Patient p = a.getPatient();
        List<PrescriptionResponse> rxList = r.getPrescriptions().stream()
                .map(PrescriptionResponse::from)
                .toList();
        return new MedicalRecordResponse(
                r.getId(),
                a.getId(),
                p.getId(),
                p.getFullName(),
                d.getId(),
                d.getDisplayName(),
                d.getSpecialization(),
                a.getStartTime(),
                a.getEndTime(),
                r.getDiagnosis(),
                r.getNotes(),
                rxList,
                r.getCreatedAt(),
                r.getUpdatedAt());
    }
}
