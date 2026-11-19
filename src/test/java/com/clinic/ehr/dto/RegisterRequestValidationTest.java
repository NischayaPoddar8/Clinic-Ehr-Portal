package com.clinic.ehr.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.clinic.ehr.model.enums.Gender;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Bean Validation rules of the registration DTOs (no Spring context needed). */
class RegisterRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private RegisterRequest request(String email, String password, LocalDate dob, Gender gender) {
        return new RegisterRequest("Nina Patient", email, password, "9876543210", dob, gender, null, null, null);
    }

    private Set<String> invalidFields(Object request) {
        return validator.validate(request).stream()
                .map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    @Test
    void validRequestPasses() {
        assertThat(validator.validate(request("nina@example.com", "Passw0rd1", LocalDate.of(1995, 3, 4), Gender.FEMALE)))
                .isEmpty();
    }

    @Test
    void rejectsBadEmailWeakPasswordFutureBirthDateAndMissingGender() {
        assertThat(invalidFields(request("not-an-email", "Passw0rd1", LocalDate.of(1995, 3, 4), Gender.FEMALE)))
                .containsExactly("email");
        assertThat(invalidFields(request("a@b.com", "short1", LocalDate.of(1995, 3, 4), Gender.FEMALE)))
                .containsExactly("password");
        assertThat(invalidFields(request("a@b.com", "onlyletters", LocalDate.of(1995, 3, 4), Gender.FEMALE)))
                .containsExactly("password");
        assertThat(invalidFields(request("a@b.com", "Passw0rd1", LocalDate.now().plusDays(1), Gender.FEMALE)))
                .containsExactly("dateOfBirth");
        assertThat(invalidFields(request("a@b.com", "Passw0rd1", LocalDate.of(1995, 3, 4), null)))
                .containsExactly("gender");
    }

    @Test
    void passwordOver72CharsIsRejectedBecauseBcryptIgnoresTheRest() {
        assertThat(invalidFields(request("a@b.com", "a1".repeat(40), LocalDate.of(1995, 3, 4), Gender.OTHER)))
                .containsExactly("password");
    }

    @Test
    void doctorSlotMinutesMustBeInRange() {
        CreateDoctorRequest tooShort = new CreateDoctorRequest("D", "d@x.com", "Passw0rd1", null, "ENT", "L1", null, 5);
        CreateDoctorRequest ok = new CreateDoctorRequest("D", "d@x.com", "Passw0rd1", null, "ENT", "L1", null, 30);
        assertThat(invalidFields(tooShort)).containsExactly("slotMinutes");
        assertThat(validator.validate(ok)).isEmpty();
    }
}
