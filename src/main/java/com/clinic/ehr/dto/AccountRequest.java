package com.clinic.ehr.dto;

/** Minimum data every account-creation request carries. Implemented by the request records. */
public interface AccountRequest {
    String email();
    String password();
}
