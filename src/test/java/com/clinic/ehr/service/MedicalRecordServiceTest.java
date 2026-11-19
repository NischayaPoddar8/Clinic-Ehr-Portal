package com.clinic.ehr.service;

import static com.clinic.ehr.support.TestData.doctor;
import static com.clinic.ehr.support.TestData.patient;
import static com.clinic.ehr.support.TestData.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clinic.ehr.dto.MedicalRecordRequest;
import com.clinic.ehr.dto.MedicalRecordResponse;
import com.clinic.ehr.dto.PrescriptionItemRequest;
import com.clinic.ehr.exception.DuplicateResourceException;
import com.clinic.ehr.exception.InvalidBookingException;
import com.clinic.ehr.exception.ResourceNotFoundException;
import com.clinic.ehr.exception.UnauthorizedAccessException;
import com.clinic.ehr.model.Appointment;
import com.clinic.ehr.model.Doctor;
import com.clinic.ehr.model.MedicalRecord;
import com.clinic.ehr.model.Patient;
import com.clinic.ehr.model.enums.AuditAction;
import com.clinic.ehr.model.enums.AuditEntityType;
import com.clinic.ehr.repository.AppointmentRepository;
import com.clinic.ehr.repository.MedicalRecordRepository;
import com.clinic.ehr.repository.PatientRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MedicalRecordServiceTest {

    private MedicalRecordRepository medicalRecordRepository;
    private AppointmentRepository appointmentRepository;
    private PatientRepository patientRepository;
    private AuditService auditService;
    private MedicalRecordService service;

    private Doctor doctor;
    private Doctor otherDoctor;
    private Patient patient;
    private Patient otherPatient;

    @BeforeEach
    void setUp() {
        medicalRecordRepository = mock(MedicalRecordRepository.class);
        appointmentRepository = mock(AppointmentRepository.class);
        patientRepository = mock(PatientRepository.class);
        auditService = mock(AuditService.class);
        service = new MedicalRecordService(medicalRecordRepository, appointmentRepository, patientRepository, auditService);

        doctor = doctor(1L);
        otherDoctor = doctor(2L);
        patient = patient(10L);
        otherPatient = patient(20L);
    }

    private Appointment completedAppointment(long id) {
        Appointment a = withId(new Appointment(patient, doctor, LocalDateTime.of(2030, 1, 15, 10, 0), "checkup"), id);
        a.complete();
        when(appointmentRepository.findById(id)).thenReturn(Optional.of(a));
        return a;
    }

    private MedicalRecord storedRecord(long recordId, Appointment appointment) {
        MedicalRecord record = withId(new MedicalRecord(appointment, "Hypertension", "Monitor BP"), recordId);
        when(medicalRecordRepository.findById(recordId)).thenReturn(Optional.of(record));
        return record;
    }

    @Test
    void createWithPrescriptionsSavesAndAudits() {
        Appointment a = completedAppointment(100L);
        when(medicalRecordRepository.findByAppointmentId(100L)).thenReturn(Optional.empty());
        when(medicalRecordRepository.save(any(MedicalRecord.class))).thenAnswer(inv -> withId(inv.getArgument(0), 50L));

        MedicalRecordRequest request = new MedicalRecordRequest("Hypertension", "Monitor BP",
                List.of(new PrescriptionItemRequest("Amlodipine", "5mg", "once daily", 30, "Morning")));

        MedicalRecordResponse response = service.create(100L, 1L, request);

        ArgumentCaptor<MedicalRecord> captor = ArgumentCaptor.forClass(MedicalRecord.class);
        verify(medicalRecordRepository).save(captor.capture());
        MedicalRecord saved = captor.getValue();
        assertThat(saved.getDiagnosis()).isEqualTo("Hypertension");
        assertThat(saved.getNotes()).isEqualTo("Monitor BP");
        assertThat(saved.getPrescriptions()).hasSize(1);
        assertThat(saved.getPrescriptions().get(0).getMedication()).isEqualTo("Amlodipine");
        assertThat(response.id()).isEqualTo(50L);
        assertThat(response.prescriptions()).hasSize(1);
        assertThat(response.doctorName()).isEqualTo("Dr. Dan Doctor");
        assertThat(response.patientName()).isEqualTo("Pat Patient");

        verify(auditService).record(same(doctor), eq(AuditAction.CREATE), eq(AuditEntityType.MEDICAL_RECORD), eq(50L), anyString());
    }

    @Test
    void createRejectsNonAssignedDoctor() {
        completedAppointment(100L);
        MedicalRecordRequest request = new MedicalRecordRequest("Flu", null, null);
        assertThatThrownBy(() -> service.create(100L, 2L, request))
                .isInstanceOf(UnauthorizedAccessException.class);
        verify(auditService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void createRejectsNonCompletedAppointment() {
        Appointment a = withId(new Appointment(patient, doctor, LocalDateTime.of(2030, 1, 15, 10, 0), "checkup"), 100L);
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(a));

        MedicalRecordRequest request = new MedicalRecordRequest("Flu", null, null);
        assertThatThrownBy(() -> service.create(100L, 1L, request))
                .isInstanceOf(InvalidBookingException.class);
    }

    @Test
    void createRejectsDuplicateRecordForAppointment() {
        Appointment a = completedAppointment(100L);
        MedicalRecord existing = withId(new MedicalRecord(a, "Existing", null), 50L);
        when(medicalRecordRepository.findByAppointmentId(100L)).thenReturn(Optional.of(existing));

        MedicalRecordRequest request = new MedicalRecordRequest("Flu", null, null);
        assertThatThrownBy(() -> service.create(100L, 1L, request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void createRejectsUnknownAppointment() {
        when(appointmentRepository.findById(404L)).thenReturn(Optional.empty());
        MedicalRecordRequest request = new MedicalRecordRequest("Flu", null, null);
        assertThatThrownBy(() -> service.create(404L, 1L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateModifiesDiagnosisNotesAndReplacesPrescriptionsAndAudits() {
        Appointment a = completedAppointment(100L);
        MedicalRecord record = storedRecord(50L, a);
        record.addPrescription("OldDrug", "10mg", "daily", 5);
        when(medicalRecordRepository.save(any(MedicalRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        MedicalRecordRequest request = new MedicalRecordRequest("Updated Diagnosis", "Updated Notes",
                List.of(new PrescriptionItemRequest("NewDrug", "20mg", "twice daily", 10, "After meals")));

        MedicalRecordResponse response = service.update(50L, 1L, request);

        assertThat(record.getDiagnosis()).isEqualTo("Updated Diagnosis");
        assertThat(record.getNotes()).isEqualTo("Updated Notes");
        assertThat(record.getPrescriptions()).hasSize(1);
        assertThat(record.getPrescriptions().get(0).getMedication()).isEqualTo("NewDrug");
        assertThat(response.prescriptions()).hasSize(1);

        verify(auditService).record(same(doctor), eq(AuditAction.UPDATE), eq(AuditEntityType.MEDICAL_RECORD), eq(50L), anyString());
    }

    @Test
    void updateRejectsNonAssignedDoctor() {
        Appointment a = completedAppointment(100L);
        storedRecord(50L, a);
        MedicalRecordRequest request = new MedicalRecordRequest("Updated", null, null);
        assertThatThrownBy(() -> service.update(50L, 2L, request))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    void updateRejectsUnknownRecord() {
        when(medicalRecordRepository.findById(404L)).thenReturn(Optional.empty());
        MedicalRecordRequest request = new MedicalRecordRequest("Updated", null, null);
        assertThatThrownBy(() -> service.update(404L, 1L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getForDoctorReturnsResponse() {
        Appointment a = completedAppointment(100L);
        MedicalRecord record = storedRecord(50L, a);
        MedicalRecordResponse response = service.getForDoctor(50L, 1L);
        assertThat(response.id()).isEqualTo(50L);
        assertThat(response.diagnosis()).isEqualTo("Hypertension");
    }

    @Test
    void getForDoctorRejectsDifferentDoctor() {
        Appointment a = completedAppointment(100L);
        storedRecord(50L, a);
        assertThatThrownBy(() -> service.getForDoctor(50L, 2L))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    void listForDoctorByPatientReturnsRecords() {
        Appointment a = completedAppointment(100L);
        MedicalRecord record = storedRecord(50L, a);
        when(patientRepository.existsById(10L)).thenReturn(true);
        when(medicalRecordRepository.findByAppointmentPatientIdAndAppointmentDoctorIdOrderByCreatedAtDesc(10L, 1L))
                .thenReturn(List.of(record));

        List<MedicalRecordResponse> list = service.listForDoctorByPatient(1L, 10L);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).id()).isEqualTo(50L);
    }

    @Test
    void listForDoctorByPatientRejectsUnknownPatient() {
        when(patientRepository.existsById(404L)).thenReturn(false);
        assertThatThrownBy(() -> service.listForDoctorByPatient(1L, 404L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listForPatientReturnsRecords() {
        Appointment a = completedAppointment(100L);
        MedicalRecord record = storedRecord(50L, a);
        when(medicalRecordRepository.findByAppointmentPatientIdOrderByCreatedAtDesc(10L))
                .thenReturn(List.of(record));

        List<MedicalRecordResponse> list = service.listForPatient(10L);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).id()).isEqualTo(50L);
    }

    @Test
    void getForPatientReturnsResponse() {
        Appointment a = completedAppointment(100L);
        MedicalRecord record = storedRecord(50L, a);
        MedicalRecordResponse response = service.getForPatient(50L, 10L);
        assertThat(response.id()).isEqualTo(50L);
        assertThat(response.diagnosis()).isEqualTo("Hypertension");
    }

    @Test
    void getForPatientRejectsDifferentPatient() {
        Appointment a = completedAppointment(100L);
        storedRecord(50L, a);
        assertThatThrownBy(() -> service.getForPatient(50L, 20L))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    void getForPatientRejectsUnknownRecord() {
        when(medicalRecordRepository.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getForPatient(404L, 10L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
