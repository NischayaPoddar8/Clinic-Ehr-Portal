package com.clinic.ehr.service;

import com.clinic.ehr.dto.AppointmentResponse;
import com.clinic.ehr.dto.BookAppointmentRequest;
import com.clinic.ehr.dto.RescheduleRequest;
import com.clinic.ehr.exception.AppointmentConflictException;
import com.clinic.ehr.exception.InvalidBookingException;
import com.clinic.ehr.exception.ResourceNotFoundException;
import com.clinic.ehr.exception.UnauthorizedAccessException;
import com.clinic.ehr.model.Appointment;
import com.clinic.ehr.model.Doctor;
import com.clinic.ehr.model.Patient;
import com.clinic.ehr.model.User;
import com.clinic.ehr.model.enums.AppointmentStatus;
import com.clinic.ehr.model.enums.AuditAction;
import com.clinic.ehr.model.enums.AuditEntityType;
import com.clinic.ehr.model.enums.Role;
import com.clinic.ehr.repository.AppointmentRepository;
import com.clinic.ehr.repository.DoctorRepository;
import com.clinic.ehr.repository.PatientRepository;
import com.clinic.ehr.repository.UserRepository;
import com.clinic.ehr.service.booking.BookingContext;
import com.clinic.ehr.service.booking.BookingRule;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Booking, cancelling, rescheduling and listing appointments.
 *
 * Locking protocol (keeps MySQL's repeatable-read snapshot fresh and avoids deadlocks): the first statement of a
 * write transaction is always a locking read. Order is appointment row, then doctor row. Booking locks only the doctor.
 */
@Service
public class AppointmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final List<BookingRule> rules; // injected in @Order sequence

    public AppointmentService(AppointmentRepository appointmentRepository, DoctorRepository doctorRepository,
                              PatientRepository patientRepository, UserRepository userRepository,
                              AuditService auditService, List<BookingRule> rules) {
        this.appointmentRepository = appointmentRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.rules = rules;
    }

    @Transactional
    public AppointmentResponse book(Long patientId, BookAppointmentRequest request) {
        Doctor doctor = lockActiveDoctor(request.doctorId());
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found: " + patientId));
        LocalDateTime start = request.startTime();
        runRules(new BookingContext(doctor, patient, start, start.plusMinutes(doctor.getSlotMinutes()), null));

        Appointment saved = appointmentRepository.save(new Appointment(patient, doctor, start, request.reason()));
        auditService.record(patient, AuditAction.BOOK, AuditEntityType.APPOINTMENT, saved.getId(),
                "Booked " + doctor.getDisplayName() + " at " + saved.getStartTime());
        log.info("Appointment {} booked: patient={} doctor={} start={}", saved.getId(), patientId, doctor.getId(), start);
        return AppointmentResponse.from(saved);
    }

    /** Cancel by the owning patient or the assigned doctor; the slot becomes bookable again. */
    @Transactional
    public AppointmentResponse cancel(Long appointmentId, Long userId, Role role) {
        Appointment appointment = lockAppointment(appointmentId);
        requireParticipant(appointment, userId, role);
        appointment.cancel(); // BOOKED -> CANCELLED, clears slotKey
        User actor = (role == Role.PATIENT) ? appointment.getPatient() : appointment.getDoctor();
        auditService.record(actor, AuditAction.CANCEL, AuditEntityType.APPOINTMENT, appointmentId,
                "Cancelled appointment of " + appointment.getStartTime());
        log.info("Appointment {} cancelled by {} {}", appointmentId, role, userId);
        return AppointmentResponse.from(appointment);
    }

    /**
     * Mark an appointment COMPLETED. Only the assigned doctor may complete their own appointment.
     * Only BOOKED appointments can transition to COMPLETED.
     */
    @Transactional
    public AppointmentResponse complete(Long appointmentId, Long doctorId) {
        Appointment appointment = lockAppointment(appointmentId);
        if (!appointment.getDoctor().getId().equals(doctorId)) {
            throw new UnauthorizedAccessException("You may not complete this appointment");
        }
        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw new InvalidBookingException(
                    "Only BOOKED appointments can be completed (current: " + appointment.getStatus() + ")");
        }
        appointment.complete();
        auditService.record(appointment.getDoctor(), AuditAction.COMPLETE, AuditEntityType.APPOINTMENT, appointmentId,
                "Completed appointment with " + appointment.getPatient().getFullName());
        log.info("Appointment {} completed by doctor {}", appointmentId, doctorId);
        return AppointmentResponse.from(appointment);
    }

    @Transactional
    public AppointmentResponse reschedule(Long appointmentId, Long patientId, RescheduleRequest request) {
        Appointment appointment = lockAppointment(appointmentId);
        requireParticipant(appointment, patientId, Role.PATIENT);
        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw new AppointmentConflictException(
                    "Only BOOKED appointments can be rescheduled (current: " + appointment.getStatus() + ")");
        }
        Doctor doctor = lockActiveDoctor(appointment.getDoctor().getId());
        Patient patient = appointment.getPatient();
        LocalDateTime newStart = request.newStartTime();
        LocalDateTime newEnd = newStart.plusMinutes(doctor.getSlotMinutes());
        runRules(new BookingContext(doctor, patient, newStart, newEnd, appointment.getId()));

        LocalDateTime oldStart = appointment.getStartTime();
        appointment.reschedule(newStart, newEnd);
        auditService.record(patient, AuditAction.RESCHEDULE, AuditEntityType.APPOINTMENT, appointmentId,
                "Moved from " + oldStart + " to " + newStart);
        log.info("Appointment {} rescheduled {} -> {}", appointmentId, oldStart, newStart);
        return AppointmentResponse.from(appointment);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> listForPatient(Long patientId, AppointmentStatus status) {
        return appointmentRepository.findByPatientIdOrderByStartTimeDesc(patientId).stream()
                .filter(a -> status == null || a.getStatus() == status)
                .map(AppointmentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> listForDoctor(Long doctorId, LocalDate date, AppointmentStatus status) {
        return appointmentRepository.findByDoctorIdOrderByStartTimeDesc(doctorId).stream()
                .filter(a -> status == null || a.getStatus() == status)
                .filter(a -> date == null || a.getStartTime().toLocalDate().equals(date))
                .map(AppointmentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> listForAdmin(Long doctorId, Long patientId, AppointmentStatus status,
                                                  LocalDate date, int page, int size) {
        LocalDateTime from = (date != null) ? date.atStartOfDay() : null;
        LocalDateTime to = (date != null) ? date.plusDays(1).atStartOfDay() : null;
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return appointmentRepository.findFilteredForAdmin(doctorId, patientId, status, from, to,
                        PageRequest.of(safePage, safeSize))
                .stream()
                .map(AppointmentResponse::from)
                .toList();
    }

    @Transactional
    public AppointmentResponse adminCancel(Long appointmentId, Long adminId) {
        Appointment appointment = lockAppointment(appointmentId);
        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw new InvalidBookingException("Only BOOKED appointments can be cancelled (current: " + appointment.getStatus() + ")");
        }
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found: " + adminId));
        appointment.cancel();
        auditService.record(admin, AuditAction.CANCEL, AuditEntityType.APPOINTMENT, appointmentId,
                "Admin cancelled appointment of " + appointment.getStartTime());
        log.info("Appointment {} cancelled by admin {}", appointmentId, adminId);
        return AppointmentResponse.from(appointment);
    }

    private void runRules(BookingContext context) {
        rules.forEach(rule -> rule.validate(context));
    }

    private Doctor lockActiveDoctor(Long doctorId) {
        return doctorRepository.findByIdForUpdate(doctorId)
                .filter(Doctor::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + doctorId));
    }

    private Appointment lockAppointment(Long appointmentId) {
        return appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + appointmentId));
    }

    private void requireParticipant(Appointment appointment, Long userId, Role role) {
        boolean allowed = switch (role) {
            case PATIENT -> appointment.getPatient().getId().equals(userId);
            case DOCTOR -> appointment.getDoctor().getId().equals(userId);
            case ADMIN -> false;
        };
        if (!allowed) {
            throw new UnauthorizedAccessException("You may not modify this appointment");
        }
    }
}
