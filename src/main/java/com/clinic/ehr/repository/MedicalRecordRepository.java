package com.clinic.ehr.repository;

import com.clinic.ehr.model.MedicalRecord;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Long> {

    @EntityGraph(attributePaths = {"appointment", "appointment.doctor", "appointment.patient", "prescriptions"})
    @Override
    Optional<MedicalRecord> findById(Long id);

    @EntityGraph(attributePaths = {"appointment", "appointment.doctor", "appointment.patient", "prescriptions"})
    Optional<MedicalRecord> findByAppointmentId(Long appointmentId);

    @EntityGraph(attributePaths = {"appointment", "appointment.doctor", "appointment.patient", "prescriptions"})
    List<MedicalRecord> findByAppointmentPatientIdOrderByCreatedAtDesc(Long patientId);

    @EntityGraph(attributePaths = {"appointment", "appointment.doctor", "appointment.patient", "prescriptions"})
    List<MedicalRecord> findByAppointmentDoctorIdOrderByCreatedAtDesc(Long doctorId);

    @EntityGraph(attributePaths = {"appointment", "appointment.doctor", "appointment.patient", "prescriptions"})
    List<MedicalRecord> findByAppointmentPatientIdAndAppointmentDoctorIdOrderByCreatedAtDesc(Long patientId, Long doctorId);
}
