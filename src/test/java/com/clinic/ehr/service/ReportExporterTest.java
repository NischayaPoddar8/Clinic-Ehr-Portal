package com.clinic.ehr.service;

import static com.clinic.ehr.support.TestData.doctor;
import static com.clinic.ehr.support.TestData.patient;
import static com.clinic.ehr.support.TestData.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clinic.ehr.exception.InvalidBookingException;
import com.clinic.ehr.exception.ResourceNotFoundException;
import com.clinic.ehr.model.Appointment;
import com.clinic.ehr.model.Doctor;
import com.clinic.ehr.model.Patient;
import com.clinic.ehr.model.User;
import com.clinic.ehr.model.enums.AuditAction;
import com.clinic.ehr.model.enums.AuditEntityType;
import com.clinic.ehr.model.enums.Role;
import com.clinic.ehr.repository.AppointmentRepository;
import com.clinic.ehr.repository.UserRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportExporterTest {

    private AppointmentRepository appointmentRepository;
    private UserRepository userRepository;
    private AuditService auditService;
    private ReportExporter exporter;
    private User admin;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        appointmentRepository = mock(AppointmentRepository.class);
        userRepository = mock(UserRepository.class);
        auditService = mock(AuditService.class);
        exporter = new ReportExporter(appointmentRepository, userRepository, auditService, tempDir.toString());

        admin = withId(new User("admin@test.local", "hash", "Admin", null, Role.ADMIN), 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
    }

    @Test
    void exportAppointmentsGeneratesCsvWithProperHeadersAndEscapedData() throws IOException {
        LocalDate from = LocalDate.of(2030, 1, 1);
        LocalDate to = LocalDate.of(2030, 1, 31);

        Doctor d = doctor(5L);
        Patient p = patient(7L);
        Appointment a = withId(new Appointment(p, d, LocalDateTime.of(2030, 1, 15, 10, 0), "Checkup, \"follow-up\""), 100L);

        when(appointmentRepository.findBetweenOrderByStartTimeAsc(from.atStartOfDay(), to.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(a));

        ReportExporter.ExportResult result = exporter.exportAppointments(from, to, "csv", 1L);

        assertThat(result.mediaType()).isEqualTo("text/csv; charset=UTF-8");
        assertThat(result.filename()).isEqualTo("appointments-report.csv");

        String csv = new String(result.content(), StandardCharsets.UTF_8);
        assertThat(csv).contains("Appointment ID,Doctor,Specialization,Patient,Start Time,End Time,Status,Reason");
        assertThat(csv).contains("100,Dr. Dan Doctor,Cardiology,Pat Patient,2030-01-15 10:00,2030-01-15 10:30,BOOKED,\"Checkup, \"\"follow-up\"\"\"");

        // Verify file was written to disk in exports directory
        assertThat(Files.list(tempDir).findAny()).isPresent();

        verify(auditService).record(same(admin), eq(AuditAction.EXPORT), eq(AuditEntityType.REPORT), eq(0L), anyString());
    }

    @Test
    void exportAppointmentsGeneratesPlainTextReport() {
        LocalDate from = LocalDate.of(2030, 1, 1);
        LocalDate to = LocalDate.of(2030, 1, 31);

        Doctor d = doctor(5L);
        Patient p = patient(7L);
        Appointment a = withId(new Appointment(p, d, LocalDateTime.of(2030, 1, 15, 10, 0), "Fever"), 100L);

        when(appointmentRepository.findBetweenOrderByStartTimeAsc(from.atStartOfDay(), to.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(a));

        ReportExporter.ExportResult result = exporter.exportAppointments(from, to, "txt", 1L);

        assertThat(result.mediaType()).isEqualTo("text/plain; charset=UTF-8");
        assertThat(result.filename()).isEqualTo("appointments-report.txt");

        String txt = new String(result.content(), StandardCharsets.UTF_8);
        assertThat(txt).contains("CLINIC APPOINTMENT REPORT (2030-01-01 to 2030-01-31)");
        assertThat(txt).contains("Total Appointments: 1");
        assertThat(txt).contains("[100] 2030-01-15 10:00 - 2030-01-15 10:30 | Doctor: Dr. Dan Doctor");
    }

    @Test
    void exportAppointmentsRejectsInvalidDateRange() {
        LocalDate from = LocalDate.of(2030, 2, 1);
        LocalDate to = LocalDate.of(2030, 1, 1);

        assertThatThrownBy(() -> exporter.exportAppointments(from, to, "csv", 1L))
                .isInstanceOf(InvalidBookingException.class);

        assertThatThrownBy(() -> exporter.exportAppointments(null, to, "csv", 1L))
                .isInstanceOf(InvalidBookingException.class);
    }

    @Test
    void exportAppointmentsRejectsUnknownAdmin() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        LocalDate from = LocalDate.of(2030, 1, 1);
        LocalDate to = LocalDate.of(2030, 1, 31);

        assertThatThrownBy(() -> exporter.exportAppointments(from, to, "csv", 999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
