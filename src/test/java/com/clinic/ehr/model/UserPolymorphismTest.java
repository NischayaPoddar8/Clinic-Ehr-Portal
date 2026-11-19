package com.clinic.ehr.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.clinic.ehr.model.enums.Gender;
import com.clinic.ehr.model.enums.Role;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserPolymorphismTest {

    @Test
    void displayNameIsResolvedPerRuntimeType() {
        List<User> users = List.of(
                new User("a@x.com", "h", "Alice Admin", "1", Role.ADMIN),
                new Doctor("d@x.com", "h", "Dan Doe", "2", "ENT", "LIC-1"),
                new Patient("p@x.com", "h", "Pam Poe", "3", LocalDate.of(2000, 5, 5), Gender.OTHER));

        assertThat(users).extracting(User::getDisplayName)
                .containsExactly("Alice Admin", "Dr. Dan Doe", "Pam Poe");
        assertThat(users).extracting(User::getRole)
                .containsExactly(Role.ADMIN, Role.DOCTOR, Role.PATIENT);
    }
}
