package com.clinic.ehr.service.booking;

import com.clinic.ehr.exception.AppointmentConflictException;
import com.clinic.ehr.repository.AppointmentRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(4)
public class PatientSlotConflictRule implements BookingRule {

    private final AppointmentRepository appointmentRepository;

    public PatientSlotConflictRule(AppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    @Override
    public void validate(BookingContext context) {
        if (appointmentRepository.existsSlotConflictForPatient(context.patient().getId(), context.start(),
                context.end(), context.ignoredAppointmentId())) {
            throw new AppointmentConflictException("You already have an appointment at this time");
        }
    }
}
