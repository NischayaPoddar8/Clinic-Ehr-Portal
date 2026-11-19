package com.clinic.ehr.service.booking;

import com.clinic.ehr.model.Doctor;
import com.clinic.ehr.model.Patient;
import java.time.LocalDateTime;

/**
 * What a booking rule needs to judge a request.
 *
 * @param ignoredAppointmentId appointment being rescheduled (its own old slot must not count as a conflict), else null
 */
public record BookingContext(Doctor doctor, Patient patient, LocalDateTime start, LocalDateTime end,
                             Long ignoredAppointmentId) { }
