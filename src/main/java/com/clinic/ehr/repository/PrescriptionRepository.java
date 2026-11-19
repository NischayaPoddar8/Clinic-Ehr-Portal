package com.clinic.ehr.repository;

import com.clinic.ehr.model.Prescription;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {
    List<Prescription> findByMedicalRecordId(Long medicalRecordId);
    /** All prescriptions of a patient, across their medical records. */
    List<Prescription> findByMedicalRecordAppointmentPatientId(Long patientId);
}
