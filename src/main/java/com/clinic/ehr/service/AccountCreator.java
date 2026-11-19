package com.clinic.ehr.service;

import com.clinic.ehr.dto.AccountRequest;
import com.clinic.ehr.dto.UserResponse;

/**
 * Contract for creating an account of some kind from a request type R.
 * Implementations: PatientRegistrationService, DoctorAccountService, AdminAccountService.
 */
public interface AccountCreator<R extends AccountRequest> {

    /** @param actorId id of the user performing the action, or null if the new account acts for itself (self-registration, seeding) */
    UserResponse create(R request, Long actorId);
}
