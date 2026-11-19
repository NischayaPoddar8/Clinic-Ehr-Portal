package com.clinic.ehr.service;

import com.clinic.ehr.dto.SlotResponse;
import com.clinic.ehr.exception.ResourceNotFoundException;
import com.clinic.ehr.model.Appointment;
import com.clinic.ehr.model.Doctor;
import com.clinic.ehr.repository.AppointmentRepository;
import com.clinic.ehr.repository.DoctorAvailabilityRepository;
import com.clinic.ehr.repository.DoctorRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Turns a doctor's weekly availability windows into bookable slots for a given date. */
@Service
public class SlotGenerationService {

    private final DoctorRepository doctorRepository;
    private final DoctorAvailabilityRepository availabilityRepository;
    private final AppointmentRepository appointmentRepository;
    private final Clock clock;

    public SlotGenerationService(DoctorRepository doctorRepository, DoctorAvailabilityRepository availabilityRepository,
                                 AppointmentRepository appointmentRepository, Clock clock) {
        this.doctorRepository = doctorRepository;
        this.availabilityRepository = availabilityRepository;
        this.appointmentRepository = appointmentRepository;
        this.clock = clock;
    }

    /** Free slots on the date: not overlapping a BOOKED/COMPLETED appointment and, for today, not already past. */
    @Transactional(readOnly = true)
    public List<SlotResponse> availableSlots(Long doctorId, LocalDate date) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (date.isBefore(now.toLocalDate())) {
            throw new IllegalArgumentException("Date must not be in the past");
        }
        Doctor doctor = doctorRepository.findById(doctorId)
                .filter(Doctor::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + doctorId));
        List<Appointment> held = appointmentRepository.findSlotHoldingByDoctorBetween(
                doctorId, date.atStartOfDay(), date.plusDays(1).atStartOfDay());
        int slotMinutes = doctor.getSlotMinutes();

        return candidateStarts(doctor, date).stream()
                .filter(start -> start.isAfter(now))
                .map(start -> new SlotResponse(start, start.plusMinutes(slotMinutes)))
                .filter(slot -> held.stream().noneMatch(a -> a.overlaps(slot.startTime(), slot.endTime())))
                .toList();
    }

    /** Every slot start the doctor's windows produce on that date, ignoring bookings and the clock. */
    @Transactional(readOnly = true)
    public List<LocalDateTime> candidateStarts(Doctor doctor, LocalDate date) {
        return availabilityRepository.findByDoctorIdAndDayOfWeek(doctor.getId(), date.getDayOfWeek()).stream()
                .flatMap(window -> window.generateSlotStarts(doctor.getSlotMinutes()).stream())
                .distinct()
                .sorted()
                .map(date::atTime)
                .toList();
    }
}
