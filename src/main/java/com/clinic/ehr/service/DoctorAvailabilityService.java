package com.clinic.ehr.service;

import com.clinic.ehr.dto.AvailabilityRequest;
import com.clinic.ehr.dto.AvailabilityResponse;
import com.clinic.ehr.exception.AppointmentConflictException;
import com.clinic.ehr.exception.ResourceNotFoundException;
import com.clinic.ehr.exception.UnauthorizedAccessException;
import com.clinic.ehr.model.Doctor;
import com.clinic.ehr.model.DoctorAvailability;
import com.clinic.ehr.model.enums.AuditAction;
import com.clinic.ehr.model.enums.AuditEntityType;
import com.clinic.ehr.repository.DoctorAvailabilityRepository;
import com.clinic.ehr.repository.DoctorRepository;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoctorAvailabilityService {

    private static final Comparator<DoctorAvailability> BY_DAY_THEN_START =
            Comparator.comparing(DoctorAvailability::getDayOfWeek).thenComparing(DoctorAvailability::getStartTime);

    private final DoctorRepository doctorRepository;
    private final DoctorAvailabilityRepository availabilityRepository;
    private final AuditService auditService;

    public DoctorAvailabilityService(DoctorRepository doctorRepository,
                                     DoctorAvailabilityRepository availabilityRepository, AuditService auditService) {
        this.doctorRepository = doctorRepository;
        this.availabilityRepository = availabilityRepository;
        this.auditService = auditService;
    }

    @Transactional
    public AvailabilityResponse add(Long doctorId, AvailabilityRequest request) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + doctorId));
        if (!request.startTime().isBefore(request.endTime())) {
            throw new IllegalArgumentException("End time must be after start time");
        }
        long windowMinutes = Duration.between(request.startTime(), request.endTime()).toMinutes();
        if (windowMinutes < doctor.getSlotMinutes()) {
            throw new IllegalArgumentException(
                    "The window must fit at least one slot (" + doctor.getSlotMinutes() + " minutes)");
        }
        boolean overlaps = availabilityRepository.findByDoctorIdAndDayOfWeek(doctorId, request.dayOfWeek()).stream()
                .anyMatch(existing -> existing.overlaps(request.startTime(), request.endTime()));
        if (overlaps) {
            throw new AppointmentConflictException(
                    "The window overlaps an existing availability window on " + request.dayOfWeek());
        }
        DoctorAvailability saved = availabilityRepository.save(
                new DoctorAvailability(doctor, request.dayOfWeek(), request.startTime(), request.endTime()));
        auditService.record(doctor, AuditAction.CREATE, AuditEntityType.AVAILABILITY, saved.getId(),
                "Added " + saved.getDayOfWeek() + " " + saved.getStartTime() + "-" + saved.getEndTime());
        return AvailabilityResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<AvailabilityResponse> list(Long doctorId) {
        return availabilityRepository.findByDoctorId(doctorId).stream()
                .sorted(BY_DAY_THEN_START)
                .map(AvailabilityResponse::from)
                .toList();
    }

    @Transactional
    public void delete(Long doctorId, Long availabilityId) {
        DoctorAvailability window = availabilityRepository.findById(availabilityId)
                .orElseThrow(() -> new ResourceNotFoundException("Availability not found: " + availabilityId));
        Doctor owner = window.getDoctor();
        if (!owner.getId().equals(doctorId)) {
            throw new UnauthorizedAccessException("This availability window belongs to another doctor");
        }
        availabilityRepository.delete(window);
        auditService.record(owner, AuditAction.DELETE, AuditEntityType.AVAILABILITY, availabilityId,
                "Removed " + window.getDayOfWeek() + " " + window.getStartTime() + "-" + window.getEndTime());
    }
}
