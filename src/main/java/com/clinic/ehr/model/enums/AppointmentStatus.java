package com.clinic.ehr.model.enums;

public enum AppointmentStatus {
    BOOKED, CANCELLED, COMPLETED;

    /** BOOKED and COMPLETED appointments occupy their time slot; only CANCELLED releases it. */
    public boolean holdsSlot() {
        return this != CANCELLED;
    }
}
