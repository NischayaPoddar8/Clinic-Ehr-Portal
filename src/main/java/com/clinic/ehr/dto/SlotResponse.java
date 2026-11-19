package com.clinic.ehr.dto;

import java.time.LocalDateTime;

public record SlotResponse(LocalDateTime startTime, LocalDateTime endTime) { }
