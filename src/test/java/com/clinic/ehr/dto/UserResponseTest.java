package com.clinic.ehr.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.clinic.ehr.model.Doctor;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class UserResponseTest {

    @Test
    void responseTypeHasNoPasswordFieldAndShowsDoctorDisplayName() {
        assertThat(Arrays.stream(UserResponse.class.getRecordComponents()).map(c -> c.getName()))
                .noneMatch(name -> name.toLowerCase().contains("password"));

        Doctor doctor = new Doctor("d@x.com", "SECRET-HASH", "Dan", "1", "ENT", "LIC-1");
        UserResponse response = UserResponse.from(doctor);
        assertThat(response.displayName()).isEqualTo("Dr. Dan");
        assertThat(response.toString()).doesNotContain("SECRET-HASH");
    }

    @Test
    void requestRecordsDoNotPrintPasswords() {
        assertThat(new LoginRequest("a@x.com", "Secret123").toString()).doesNotContain("Secret123");
        assertThat(new AdminSeedRequest("a@x.com", "Secret123", "A").toString()).doesNotContain("Secret123");
    }
}
