package com.clinic.ehr.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record BookAppointmentRequest(@NotNull Long doctorId, @NotNull LocalDateTime startTime,
                                     @Size(max = 255) String reason) { }
