package com.clinic.ehr.dto;

import com.clinic.ehr.model.DoctorAvailability;
import java.time.DayOfWeek;
import java.time.LocalTime;

public record AvailabilityResponse(Long id, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {

    public static AvailabilityResponse from(DoctorAvailability window) {
        return new AvailabilityResponse(window.getId(), window.getDayOfWeek(), window.getStartTime(), window.getEndTime());
    }
}
