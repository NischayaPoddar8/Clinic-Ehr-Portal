package com.clinic.ehr.dto;

/** Internal request used only by the startup admin seeder; not exposed through any API. */
public record AdminSeedRequest(String email, String password, String fullName) implements AccountRequest {

    @Override
    public String toString() {
        return "AdminSeedRequest[email=" + email + "]";
    }
}
