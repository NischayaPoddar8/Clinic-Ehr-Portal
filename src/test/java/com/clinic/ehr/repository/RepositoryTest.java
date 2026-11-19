package com.clinic.ehr.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.clinic.ehr.model.*;
import com.clinic.ehr.model.enums.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/** Runs against a real MySQL (schema clinic_ehr_test). Enable with: mvn test -Pmysql-tests */
@Tag("mysql")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class RepositoryTest {

    private static final LocalDateTime T10 = LocalDateTime.of(2030, 1, 15, 10, 0);

    @Autowired UserRepository users;
    @Autowired PatientRepository patients;
    @Autowired DoctorRepository doctors;
    @Autowired DoctorAvailabilityRepository availability;
    @Autowired AppointmentRepository appointments;
    @Autowired MedicalRecordRepository records;
    @Autowired PrescriptionRepository prescriptions;
    @Autowired AuditLogRepository audits;

    private Patient patient;
    private Doctor doctor;

    @BeforeEach
    void setUp() {
        patient = patients.save(new Patient("pat@x.com", "h", "Pat Patient", "1", LocalDate.of(1990, 1, 1), Gender.FEMALE));
        doctor = doctors.save(new Doctor("doc@x.com", "h", "Dan Doctor", "2", "Cardiology", "LIC-1"));
    }

    private Appointment book(LocalDateTime start) {
        return appointments.saveAndFlush(new Appointment(patient, doctor, start, "visit"));
    }

    @Test
    void usersAreFoundByEmailThroughBaseRepository() {
        assertThat(users.findByEmail("doc@x.com")).containsInstanceOf(Doctor.class);
        assertThat(users.existsByEmail("pat@x.com")).isTrue();
    }

    @Test
    void searchActiveMatchesNameOrSpecialization() {
        assertThat(doctors.searchActive("cardio", "")).containsExactly(doctor);
        assertThat(doctors.searchActive("", "dan")).containsExactly(doctor);
        assertThat(doctors.searchActive("neuro", "")).isEmpty();
    }

    @Test
    void findByIdForUpdateReturnsDoctor() {
        assertThat(doctors.findByIdForUpdate(doctor.getId())).isPresent();
    }

    @Test
    void availabilityQueriesAndUniqueWindow() {
        availability.saveAndFlush(new DoctorAvailability(doctor, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0)));
        assertThat(availability.findByDoctorId(doctor.getId())).hasSize(1);
        assertThat(availability.findByDoctorIdAndDayOfWeek(doctor.getId(), DayOfWeek.TUESDAY)).isEmpty();
    }

    @Test
    void duplicateAvailabilityStartIsRejected() {
        availability.saveAndFlush(new DoctorAvailability(doctor, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0)));
        assertThatThrownBy(() -> availability.saveAndFlush(
                new DoctorAvailability(doctor, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameSlotCannotBeBookedTwice() {
        book(T10);
        assertThatThrownBy(() -> book(T10)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void cancelledSlotCanBeBookedAgain() {
        Appointment first = book(T10);
        first.cancel();
        appointments.saveAndFlush(first);
        Appointment second = book(T10);
        assertThat(second.getId()).isNotNull();
        assertThat(appointments.findAll()).hasSize(2);
    }

    @Test
    void appointmentListsPerPatientAndDoctorAreNewestFirst() {
        book(T10);
        book(T10.plusHours(1));
        assertThat(appointments.findByPatientIdOrderByStartTimeDesc(patient.getId()))
                .extracting(Appointment::getStartTime).containsExactly(T10.plusHours(1), T10);
        assertThat(appointments.findByDoctorIdOrderByStartTimeDesc(doctor.getId())).hasSize(2);
    }

    @Test
    void takenSlotsOfADayCanBeListed() {
        book(T10);
        Appointment cancelled = book(T10.plusHours(1));
        cancelled.cancel();
        appointments.saveAndFlush(cancelled);
        List<Appointment> taken = appointments.findSlotHoldingByDoctorBetween(
                doctor.getId(), T10.toLocalDate().atStartOfDay(), T10.toLocalDate().plusDays(1).atStartOfDay());
        assertThat(taken).hasSize(1);
    }

    @Test
    void overlapQueriesIgnoreCancelledAndTouchingRanges() {
        Appointment a = book(T10); // 10:00-10:30
        assertThat(appointments.existsSlotConflictForDoctor(doctor.getId(), T10.plusMinutes(15), T10.plusMinutes(45), null)).isTrue();
        assertThat(appointments.existsSlotConflictForDoctor(doctor.getId(), T10.plusMinutes(30), T10.plusMinutes(60), null)).isFalse();
        assertThat(appointments.existsSlotConflictForPatient(patient.getId(), T10, T10.plusMinutes(30), null)).isTrue();
        a.cancel();
        appointments.saveAndFlush(a);
        assertThat(appointments.existsSlotConflictForDoctor(doctor.getId(), T10, T10.plusMinutes(30), null)).isFalse();
    }

    @Test
    void conflictQueryCanIgnoreTheAppointmentBeingRescheduled() {
        Appointment a = book(T10); // 10:00-10:30
        assertThat(appointments.existsSlotConflictForDoctor(doctor.getId(), T10, T10.plusMinutes(30), a.getId())).isFalse();
        assertThat(appointments.existsSlotConflictForPatient(patient.getId(), T10, T10.plusMinutes(30), a.getId())).isFalse();
        assertThat(appointments.existsSlotConflictForDoctor(doctor.getId(), T10, T10.plusMinutes(30), a.getId() + 1)).isTrue();
    }

    @Test
    void appointmentRowCanBeLockedById() {
        Appointment a = book(T10);
        assertThat(appointments.findByIdForUpdate(a.getId())).isPresent();
    }

    @Test
    void inactiveDoctorsAreNotSearchable() {
        doctor.setActive(false);
        doctors.saveAndFlush(doctor);
        assertThat(doctors.searchActive("", "")).isEmpty();
    }

    @Test
    void completedAppointmentStillBlocksOverlappingBookings() {
        Appointment a = book(T10);
        a.complete();
        appointments.saveAndFlush(a);
        assertThat(appointments.existsSlotConflictForDoctor(doctor.getId(), T10.plusMinutes(10), T10.plusMinutes(40), null)).isTrue();
        assertThat(appointments.existsSlotConflictForPatient(patient.getId(), T10, T10.plusMinutes(30), null)).isTrue();
    }

    @Test
    void medicalRecordCascadesPrescriptionsAndIsQueryableByPatient() {
        Appointment a = book(T10);
        MedicalRecord record = new MedicalRecord(a, "Flu", "Rest and fluids");
        record.addPrescription("Paracetamol", "500mg", "3x daily", 5);
        records.saveAndFlush(record);

        assertThat(records.findByAppointmentId(a.getId())).isPresent();
        assertThat(records.findByAppointmentPatientIdOrderByCreatedAtDesc(patient.getId())).hasSize(1);
        assertThat(records.findByAppointmentDoctorIdOrderByCreatedAtDesc(doctor.getId())).hasSize(1);
        assertThat(prescriptions.findByMedicalRecordId(record.getId())).hasSize(1);
        assertThat(prescriptions.findByMedicalRecordAppointmentPatientId(patient.getId())).hasSize(1);
    }

    @Test
    void onlyOneMedicalRecordPerAppointment() {
        Appointment a = book(T10);
        records.saveAndFlush(new MedicalRecord(a, "Flu", null));
        assertThatThrownBy(() -> records.saveAndFlush(new MedicalRecord(a, "Cold", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void auditLogsAreFoundByActorEntityAndPaged() {
        Appointment a = book(T10);
        audits.save(new AuditLog(patient, AuditAction.BOOK, AuditEntityType.APPOINTMENT, a.getId(), "booked"));
        audits.saveAndFlush(new AuditLog(doctor, AuditAction.COMPLETE, AuditEntityType.APPOINTMENT, a.getId(), "done"));

        assertThat(audits.findByActorIdOrderByCreatedAtDesc(patient.getId())).hasSize(1);
        assertThat(audits.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(AuditEntityType.APPOINTMENT, a.getId())).hasSize(2);
        assertThat(audits.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 1))).hasSize(1);
    }
}
