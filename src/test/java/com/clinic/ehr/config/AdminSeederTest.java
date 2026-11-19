package com.clinic.ehr.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clinic.ehr.dto.AdminSeedRequest;
import com.clinic.ehr.repository.UserRepository;
import com.clinic.ehr.service.AdminAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AdminSeederTest {

    private UserRepository userRepository;
    private AdminAccountService adminService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        adminService = mock(AdminAccountService.class);
    }

    private AdminSeeder seeder(String email, String password) {
        return new AdminSeeder(userRepository, adminService, email, password, "Root Admin");
    }

    @Test
    void createsAdminWhenConfiguredAndAbsent() {
        assertThat(seeder("Admin@Clinic.com", "Str0ngPass").seedAdmin()).isTrue();
        ArgumentCaptor<AdminSeedRequest> request = ArgumentCaptor.forClass(AdminSeedRequest.class);
        verify(adminService).create(request.capture(), isNull());
        assertThat(request.getValue().fullName()).isEqualTo("Root Admin");
    }

    @Test
    void skipsWhenNotConfigured() {
        assertThat(seeder("", "").seedAdmin()).isFalse();
        assertThat(seeder("admin@clinic.com", "  ").seedAdmin()).isFalse();
        verify(adminService, never()).create(any(), any());
    }

    @Test
    void skipsWeakPassword() {
        assertThat(seeder("admin@clinic.com", "short").seedAdmin()).isFalse();
        verify(adminService, never()).create(any(), any());
    }

    @Test
    void skipsWhenAdminAlreadyExists() {
        when(userRepository.existsByEmail("admin@clinic.com")).thenReturn(true);
        assertThat(seeder("Admin@clinic.com", "Str0ngPass").seedAdmin()).isFalse();
        verify(adminService, never()).create(any(), any());
    }
}
