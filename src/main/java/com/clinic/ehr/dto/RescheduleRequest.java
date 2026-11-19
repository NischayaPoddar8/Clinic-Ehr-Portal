package com.clinic.ehr.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record RescheduleRequest(@NotNull LocalDateTime newStartTime) { }
