package com.clinic.ehr.model;

import com.clinic.ehr.model.enums.AppointmentStatus;
import jakarta.persistence.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * A booked slot between a patient and a doctor.
 *
 * Double-booking guard: {@code slotKey} is "doctorId_yyyyMMddHHmm" while the slot is held (BOOKED or COMPLETED)
 * and NULL once CANCELLED. A UNIQUE index on slot_key lets MySQL reject a second booking of the same slot,
 * while the many NULLs of cancelled rows never collide, so cancelled slots can be rebooked.
 */
@Entity
@Table(name = "appointments",
       uniqueConstraints = @UniqueConstraint(name = "uk_appointments_slot_key", columnNames = "slot_key"),
       indexes = {
           @Index(name = "idx_appointments_doctor_start", columnList = "doctor_id,start_time"),
           @Index(name = "idx_appointments_patient_start", columnList = "patient_id,start_time")
       })
public class Appointment {

    private static final DateTimeFormatter SLOT_KEY_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private AppointmentStatus status;

    @Column(length = 255)
    private String reason;

    @Column(name = "slot_key", length = 40)
    private String slotKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    protected Appointment() { }

    /** Creates a BOOKED appointment lasting one of the doctor's slots. The doctor must already be persisted. */
    public Appointment(Patient patient, Doctor doctor, LocalDateTime startTime, String reason) {
        this.patient = Objects.requireNonNull(patient, "patient");
        this.doctor = Objects.requireNonNull(doctor, "doctor");
        Objects.requireNonNull(startTime, "startTime");
        if (doctor.getId() == null) {
            throw new IllegalArgumentException("Doctor must be saved before booking");
        }
        this.startTime = startTime;
        this.endTime = startTime.plusMinutes(doctor.getSlotMinutes());
        this.reason = reason;
        this.status = AppointmentStatus.BOOKED;
        this.slotKey = buildSlotKey(doctor.getId(), startTime);
    }

    public static String buildSlotKey(Long doctorId, LocalDateTime start) {
        return doctorId + "_" + SLOT_KEY_TIME.format(start);
    }

    /** BOOKED -> CANCELLED. Releases the slot. */
    public void cancel() {
        requireBooked("cancelled");
        this.status = AppointmentStatus.CANCELLED;
        this.slotKey = null;
    }

    /** BOOKED -> COMPLETED. The slot stays held because the consultation took place. */
    public void complete() {
        requireBooked("completed");
        this.status = AppointmentStatus.COMPLETED;
    }

    /** Moves a BOOKED appointment to a new start time, keeping its duration. */
    public void reschedule(LocalDateTime newStart) {
        Objects.requireNonNull(newStart, "newStart");
        reschedule(newStart, newStart.plus(getDuration()));
    }

    /** Moves a BOOKED appointment to an explicit new time range (used when the doctor's slot length changed). */
    public void reschedule(LocalDateTime newStart, LocalDateTime newEnd) {
        requireBooked("rescheduled");
        Objects.requireNonNull(newStart, "newStart");
        Objects.requireNonNull(newEnd, "newEnd");
        if (!newStart.isBefore(newEnd)) {
            throw new IllegalArgumentException("End time must be after start time");
        }
        this.startTime = newStart;
        this.endTime = newEnd;
        this.slotKey = buildSlotKey(doctor.getId(), newStart);
    }

    /** True if this appointment's time range intersects [from, to). */
    public boolean overlaps(LocalDateTime from, LocalDateTime to) {
        return startTime.isBefore(to) && endTime.isAfter(from);
    }

    public Duration getDuration() {
        return Duration.between(startTime, endTime);
    }

    private void requireBooked(String verb) {
        if (status != AppointmentStatus.BOOKED) {
            throw new IllegalStateException("Only BOOKED appointments can be " + verb + " (current: " + status + ")");
        }
    }

    @PreUpdate
    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Patient getPatient() { return patient; }
    public Doctor getDoctor() { return doctor; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public AppointmentStatus getStatus() { return status; }
    public String getReason() { return reason; }
    public String getSlotKey() { return slotKey; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
