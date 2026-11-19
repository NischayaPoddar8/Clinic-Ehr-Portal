package com.clinic.ehr.config;

import com.clinic.ehr.dto.AdminSeedRequest;
import com.clinic.ehr.repository.UserRepository;
import com.clinic.ehr.service.AdminAccountService;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the first ADMIN at startup from configuration (ADMIN_EMAIL / ADMIN_PASSWORD environment variables).
 * Nothing is seeded if either is empty, so no default credentials ever exist in source control.
 */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final AdminAccountService adminAccountService;
    private final String email;
    private final String password;
    private final String fullName;

    public AdminSeeder(UserRepository userRepository, AdminAccountService adminAccountService,
                       @Value("${app.admin.email:}") String email,
                       @Value("${app.admin.password:}") String password,
                       @Value("${app.admin.name:System Administrator}") String fullName) {
        this.userRepository = userRepository;
        this.adminAccountService = adminAccountService;
        this.email = email;
        this.password = password;
        this.fullName = fullName;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedAdmin();
    }

    /** @return true if an admin account was created by this call */
    public boolean seedAdmin() {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.warn("No admin account seeded: set ADMIN_EMAIL and ADMIN_PASSWORD to create one");
            return false;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            log.error("Admin account not seeded: ADMIN_PASSWORD must have at least {} characters", MIN_PASSWORD_LENGTH);
            return false;
        }
        if (userRepository.existsByEmail(email.trim().toLowerCase(Locale.ROOT))) {
            log.info("Admin account already exists; seeding skipped");
            return false;
        }
        adminAccountService.create(new AdminSeedRequest(email, password, fullName), null);
        log.info("Admin account created for {}", email.trim().toLowerCase(Locale.ROOT));
        return true;
    }
}
