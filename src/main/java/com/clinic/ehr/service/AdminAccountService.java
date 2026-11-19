package com.clinic.ehr.service;

import com.clinic.ehr.dto.AdminSeedRequest;
import com.clinic.ehr.model.User;
import com.clinic.ehr.model.enums.Role;
import com.clinic.ehr.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Creates ADMIN accounts. Admins are plain User rows, so U = User. Used only by AdminSeeder. */
@Service
public class AdminAccountService extends AbstractAccountCreator<AdminSeedRequest, User> {

    public AdminAccountService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                               AuditService auditService) {
        super(userRepository, passwordEncoder, auditService);
    }

    @Override
    protected User build(AdminSeedRequest request, String normalizedEmail, String passwordHash) {
        return new User(normalizedEmail, passwordHash, request.fullName().trim(), null, Role.ADMIN);
    }
}
