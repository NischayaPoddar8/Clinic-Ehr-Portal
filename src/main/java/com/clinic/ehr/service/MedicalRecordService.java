package com.clinic.ehr.service;

import com.clinic.ehr.dto.MedicalRecordRequest;
import com.clinic.ehr.dto.MedicalRecordResponse;
import com.clinic.ehr.dto.PrescriptionItemRequest;
import com.clinic.ehr.exception.DuplicateResourceException;
import com.clinic.ehr.exception.InvalidBookingException;
import com.clinic.ehr.exception.ResourceNotFoundException;
import com.clinic.ehr.exception.UnauthorizedAccessException;
import com.clinic.ehr.model.Appointment;
import com.clinic.ehr.model.MedicalRecord;
import com.clinic.ehr.model.enums.AppointmentStatus;
import com.clinic.ehr.model.enums.AuditAction;
import com.clinic.ehr.model.enums.AuditEntityType;
import com.clinic.ehr.repository.AppointmentRepository;
import com.clinic.ehr.repository.MedicalRecordRepository;
import com.clinic.ehr.repository.PatientRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MedicalRecordService {

    private static final Logger log = LoggerFactory.getLogger(MedicalRecordService.class);

    private final MedicalRecordRepository medicalRecordRepository;
    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final AuditService auditService;

    public MedicalRecordService(MedicalRecordRepository medicalRecordRepository,
                                AppointmentRepository appointmentRepository,
                                PatientRepository patientRepository,
                                AuditService auditService) {
        this.medicalRecordRepository = medicalRecordRepository;
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.auditService = auditService;
    }

    @Transactional
    public MedicalRecordResponse create(Long appointmentId, Long doctorId, MedicalRecordRequest request) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + appointmentId));

        if (!appointment.getDoctor().getId().equals(doctorId)) {
            throw new UnauthorizedAccessException("You may not create a medical record for another doctor's appointment");
        }
        if (appointment.getStatus() != AppointmentStatus.COMPLETED) {
            throw new InvalidBookingException(
                    "Medical records can only be created for COMPLETED appointments (current: " + appointment.getStatus() + ")");
        }
        if (medicalRecordRepository.findByAppointmentId(appointmentId).isPresent()) {
            throw new DuplicateResourceException("A medical record already exists for appointment " + appointmentId);
        }

        MedicalRecord record = new MedicalRecord(appointment, request.diagnosis(), request.notes());
        if (request.prescriptions() != null) {
            for (PrescriptionItemRequest rx : request.prescriptions()) {
                record.addPrescription(rx.medication(), rx.dosage(), rx.frequency(), rx.durationDays(), rx.instructions());
            }
        }

        MedicalRecord saved = medicalRecordRepository.save(record);
        auditService.record(appointment.getDoctor(), AuditAction.CREATE, AuditEntityType.MEDICAL_RECORD, saved.getId(),
                "Created medical record for appointment " + appointmentId);
        log.info("Medical record {} created for appointment {} by doctor {}", saved.getId(), appointmentId, doctorId);
        return MedicalRecordResponse.from(saved);
    }

    @Transactional
    public MedicalRecordResponse update(Long recordId, Long doctorId, MedicalRecordRequest request) {
        MedicalRecord record = medicalRecordRepository.findById(recordId)
                .orElseThrow(() -> new ResourceNotFoundException("Medical record not found: " + recordId));

        if (!record.getAppointment().getDoctor().getId().equals(doctorId)) {
            throw new UnauthorizedAccessException("You may not update another doctor's medical record");
        }

        record.update(request.diagnosis(), request.notes());
        record.clearPrescriptions();
        if (request.prescriptions() != null) {
            for (PrescriptionItemRequest rx : request.prescriptions()) {
                record.addPrescription(rx.medication(), rx.dosage(), rx.frequency(), rx.durationDays(), rx.instructions());
            }
        }

        MedicalRecord saved = medicalRecordRepository.save(record);
        auditService.record(record.getAppointment().getDoctor(), AuditAction.UPDATE, AuditEntityType.MEDICAL_RECORD,
                record.getId(), "Updated medical record " + record.getId());
        log.info("Medical record {} updated by doctor {}", record.getId(), doctorId);
        return MedicalRecordResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public MedicalRecordResponse getForDoctor(Long recordId, Long doctorId) {
        MedicalRecord record = medicalRecordRepository.findById(recordId)
                .orElseThrow(() -> new ResourceNotFoundException("Medical record not found: " + recordId));

        if (!record.getAppointment().getDoctor().getId().equals(doctorId)) {
            throw new UnauthorizedAccessException("You may not view another doctor's medical record");
        }
        return MedicalRecordResponse.from(record);
    }

    @Transactional(readOnly = true)
    public List<MedicalRecordResponse> listForDoctorByPatient(Long doctorId, Long patientId) {
        if (!patientRepository.existsById(patientId)) {
            throw new ResourceNotFoundException("Patient not found: " + patientId);
        }
        return medicalRecordRepository.findByAppointmentPatientIdAndAppointmentDoctorIdOrderByCreatedAtDesc(patientId, doctorId)
                .stream()
                .map(MedicalRecordResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MedicalRecordResponse> listForPatient(Long patientId) {
        return medicalRecordRepository.findByAppointmentPatientIdOrderByCreatedAtDesc(patientId)
                .stream()
                .map(MedicalRecordResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MedicalRecordResponse getForPatient(Long recordId, Long patientId) {
        MedicalRecord record = medicalRecordRepository.findById(recordId)
                .orElseThrow(() -> new ResourceNotFoundException("Medical record not found: " + recordId));

        if (!record.getAppointment().getPatient().getId().equals(patientId)) {
            throw new UnauthorizedAccessException("You may not view another patient's medical record");
        }
        return MedicalRecordResponse.from(record);
    }
}
