package com.clinic.ehr.service;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminStatsService {

    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final Clock clock;

    public AdminStatsService(PatientRepository patientRepository,
                             DoctorRepository doctorRepository,
                             AppointmentRepository appointmentRepository,
                             MedicalRecordRepository medicalRecordRepository,
                             PrescriptionRepository prescriptionRepository,
                             Clock clock) {
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
        this.medicalRecordRepository = medicalRecordRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ClinicStatsResponse getStats() {
        long activePatients = patientRepository.countByActive(true);
        long inactivePatients = patientRepository.countByActive(false);

        long activeDoctors = doctorRepository.countByActive(true);
        long inactiveDoctors = doctorRepository.countByActive(false);

        long bookedAppointments = appointmentRepository.countByStatus(AppointmentStatus.BOOKED);
        long completedAppointments = appointmentRepository.countByStatus(AppointmentStatus.COMPLETED);
        long cancelledAppointments = appointmentRepository.countByStatus(AppointmentStatus.CANCELLED);

        long totalMedicalRecords = medicalRecordRepository.count();
        long totalPrescriptions = prescriptionRepository.count();

        LocalDate today = LocalDate.now(clock);
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();
        long todayAppointments = appointmentRepository.countBetween(startOfDay, endOfDay);

        return new ClinicStatsResponse(
                activePatients,
                inactivePatients,
                activeDoctors,
                inactiveDoctors,
                bookedAppointments,
                completedAppointments,
                cancelledAppointments,
                totalMedicalRecords,
                totalPrescriptions,
                todayAppointments
        );
    }
}
