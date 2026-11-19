package com.clinic.ehr.service.booking;

import com.clinic.ehr.exception.InvalidBookingException;
import com.clinic.ehr.model.Doctor;
import com.clinic.ehr.service.SlotGenerationService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** The request must be exactly one of the slots generated from the doctor's weekly availability. */
@Component
@Order(2)
public class DoctorAvailabilityRule implements BookingRule {

    private final SlotGenerationService slotGenerationService;

    public DoctorAvailabilityRule(SlotGenerationService slotGenerationService) {
        this.slotGenerationService = slotGenerationService;
    }

    @Override
    public void validate(BookingContext context) {
        Doctor doctor = context.doctor();
        boolean fullSlot = context.end().equals(context.start().plusMinutes(doctor.getSlotMinutes()));
        boolean generated = slotGenerationService.candidateStarts(doctor, context.start().toLocalDate())
                .contains(context.start());
        if (!fullSlot || !generated) {
            throw new InvalidBookingException("The requested time is not one of the doctor's available slots");
        }
    }
}
