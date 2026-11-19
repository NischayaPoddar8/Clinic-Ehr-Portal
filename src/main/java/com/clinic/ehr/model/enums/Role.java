package com.clinic.ehr.model.enums;

public enum Role {
    ADMIN, DOCTOR, PATIENT;

    /** Authority name used by Spring Security. */
    public String authority() {
        return "ROLE_" + name();
    }
}
