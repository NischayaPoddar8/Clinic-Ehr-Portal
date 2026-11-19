package com.clinic.ehr.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.clinic.ehr.model.Doctor;
import com.clinic.ehr.model.User;
import com.clinic.ehr.model.enums.Role;
import com.clinic.ehr.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

class CustomUserDetailsServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final CustomUserDetailsService service = new CustomUserDetailsService(userRepository);

    @Test
    void loadsAnyRoleByNormalizedEmailWithRoleAuthority() {
        Doctor doctor = new Doctor("dan@clinic.com", "HASH", "Dan", "1", "ENT", "LIC-1");
        ReflectionTestUtils.setField(doctor, "id", 4L);
        when(userRepository.findByEmail("dan@clinic.com")).thenReturn(Optional.of(doctor));

        UserDetails details = service.loadUserByUsername("  DAN@Clinic.com ");

        assertThat(details.getUsername()).isEqualTo("dan@clinic.com");
        assertThat(details.getPassword()).isEqualTo("HASH");
        assertThat(details.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_DOCTOR");
        assertThat(details.isEnabled()).isTrue();
        assertThat(((CustomUserDetails) details).getId()).isEqualTo(4L);
    }

    @Test
    void deactivatedAccountIsDisabled() {
        User user = new User("a@x.com", "H", "A", null, Role.ADMIN);
        user.setActive(false);
        when(userRepository.findByEmail("a@x.com")).thenReturn(Optional.of(user));
        assertThat(service.loadUserByUsername("a@x.com").isEnabled()).isFalse();
    }

    @Test
    void unknownEmailThrows() {
        when(userRepository.findByEmail("none@x.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.loadUserByUsername("none@x.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
