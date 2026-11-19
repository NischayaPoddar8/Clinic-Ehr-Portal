package com.clinic.ehr.model;

import jakarta.persistence.*;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A weekly recurring working window of a doctor, e.g. MONDAY 09:00-13:00. */
@Entity
@Table(name = "doctor_availability",
       uniqueConstraints = @UniqueConstraint(name = "uk_availability_doctor_day_start",
                                             columnNames = {"doctor_id", "day_of_week", "start_time"}))
public class DoctorAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    protected DoctorAvailability() { }

    public DoctorAvailability(Doctor doctor, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.doctor = Objects.requireNonNull(doctor, "doctor");
        this.dayOfWeek = Objects.requireNonNull(dayOfWeek, "dayOfWeek");
        setWindow(startTime, endTime);
    }

    /** Changes the working window; the end must be after the start. */
    public void setWindow(LocalTime startTime, LocalTime endTime) {
        Objects.requireNonNull(startTime, "startTime");
        Objects.requireNonNull(endTime, "endTime");
        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("Availability end time must be after start time");
        }
        this.startTime = startTime;
        this.endTime = endTime;
    }

    /** True if a slot [from, to) lies completely inside this window. */
    public boolean covers(LocalTime from, LocalTime to) {
        return !from.isBefore(startTime) && !to.isAfter(endTime) && from.isBefore(to);
    }

    /** True if [from, to) intersects this window (touching ends do not overlap). */
    public boolean overlaps(LocalTime from, LocalTime to) {
        return from.isBefore(endTime) && to.isAfter(startTime);
    }

    /** Start times of all whole slots that fit in the window, e.g. 09:00-10:30 @30 -> 09:00, 09:30, 10:00. */
    public List<LocalTime> generateSlotStarts(int slotMinutes) {
        if (slotMinutes <= 0) {
            throw new IllegalArgumentException("slotMinutes must be positive");
        }
        long windowMinutes = Duration.between(startTime, endTime).toMinutes();
        List<LocalTime> starts = new ArrayList<>();
        for (long offset = 0; offset + slotMinutes <= windowMinutes; offset += slotMinutes) {
            starts.add(startTime.plusMinutes(offset));
        }
        return starts;
    }

    public Long getId() { return id; }
    public Doctor getDoctor() { return doctor; }
    public DayOfWeek getDayOfWeek() { return dayOfWeek; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
}
