package com.clinic.ehr.service.booking;

import com.clinic.ehr.exception.SlotAlreadyBookedException;
import com.clinic.ehr.repository.AppointmentRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(3)
public class DoctorSlotConflictRule implements BookingRule {

    private final AppointmentRepository appointmentRepository;

    public DoctorSlotConflictRule(AppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    @Override
    public void validate(BookingContext context) {
        if (appointmentRepository.existsSlotConflictForDoctor(context.doctor().getId(), context.start(),
                context.end(), context.ignoredAppointmentId())) {
            throw new SlotAlreadyBookedException("This slot is no longer available");
        }
    }
}
