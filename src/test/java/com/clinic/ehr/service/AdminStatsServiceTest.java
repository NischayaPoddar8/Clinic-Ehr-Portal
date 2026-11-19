package com.clinic.ehr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.clinic.ehr.dto.ClinicStatsResponse;
import com.clinic.ehr.model.enums.AppointmentStatus;
import com.clinic.ehr.repository.AppointmentRepository;
import com.clinic.ehr.repository.DoctorRepository;
import com.clinic.ehr.repository.MedicalRecordRepository;
import com.clinic.ehr.repository.PatientRepository;
import com.clinic.ehr.repository.PrescriptionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminStatsServiceTest {

    private PatientRepository patientRepository;
    private DoctorRepository doctorRepository;
    private AppointmentRepository appointmentRepository;
    private MedicalRecordRepository medicalRecordRepository;
    private PrescriptionRepository prescriptionRepository;
    private Clock clock;
    private AdminStatsService service;

    private static final LocalDate TODAY = LocalDate.of(2030, 1, 14);

    @BeforeEach
    void setUp() {
        patientRepository = mock(PatientRepository.class);
        doctorRepository = mock(DoctorRepository.class);
        appointmentRepository = mock(AppointmentRepository.class);
        medicalRecordRepository = mock(MedicalRecordRepository.class);
        prescriptionRepository = mock(PrescriptionRepository.class);
        clock = Clock.fixed(TODAY.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

        service = new AdminStatsService(patientRepository, doctorRepository, appointmentRepository,
                medicalRecordRepository, prescriptionRepository, clock);
    }

    @Test
    void getStatsAggregatesClinicMetricsCorrectly() {
        when(patientRepository.countByActive(true)).thenReturn(15L);
        when(patientRepository.countByActive(false)).thenReturn(3L);

        when(doctorRepository.countByActive(true)).thenReturn(8L);
        when(doctorRepository.countByActive(false)).thenReturn(1L);

        when(appointmentRepository.countByStatus(AppointmentStatus.BOOKED)).thenReturn(20L);
        when(appointmentRepository.countByStatus(AppointmentStatus.COMPLETED)).thenReturn(35L);
        when(appointmentRepository.countByStatus(AppointmentStatus.CANCELLED)).thenReturn(5L);

        when(medicalRecordRepository.count()).thenReturn(30L);
        when(prescriptionRepository.count()).thenReturn(45L);

        LocalDateTime startOfDay = TODAY.atStartOfDay();
        LocalDateTime endOfDay = TODAY.plusDays(1).atStartOfDay();
        when(appointmentRepository.countBetween(startOfDay, endOfDay)).thenReturn(7L);

        ClinicStatsResponse stats = service.getStats();

        assertThat(stats.activePatients()).isEqualTo(15L);
        assertThat(stats.inactivePatients()).isEqualTo(3L);
        assertThat(stats.activeDoctors()).isEqualTo(8L);
        assertThat(stats.inactiveDoctors()).isEqualTo(1L);
        assertThat(stats.bookedAppointments()).isEqualTo(20L);
        assertThat(stats.completedAppointments()).isEqualTo(35L);
        assertThat(stats.cancelledAppointments()).isEqualTo(5L);
        assertThat(stats.totalMedicalRecords()).isEqualTo(30L);
        assertThat(stats.totalPrescriptions()).isEqualTo(45L);
        assertThat(stats.todayAppointments()).isEqualTo(7L);
    }
}
