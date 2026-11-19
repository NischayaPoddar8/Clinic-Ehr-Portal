package com.clinic.ehr.service;

import com.clinic.ehr.dto.AccountRequest;
import com.clinic.ehr.dto.UserResponse;
import com.clinic.ehr.exception.DuplicateResourceException;
import com.clinic.ehr.exception.ResourceNotFoundException;
import com.clinic.ehr.model.User;
import com.clinic.ehr.model.enums.AuditAction;
import com.clinic.ehr.model.enums.AuditEntityType;
import com.clinic.ehr.repository.UserRepository;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Template method for account creation. The common steps (unique email, hashing, saving, auditing)
 * live here; subclasses only decide which User subtype to build and add their own rules.
 *
 * @param <R> request type
 * @param <U> account type produced (User, Patient or Doctor)
 */
public abstract class AbstractAccountCreator<R extends AccountRequest, U extends User> implements AccountCreator<R> {

    private static final Logger log = LoggerFactory.getLogger(AbstractAccountCreator.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    protected AbstractAccountCreator(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                     AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    // Not final: Spring's transactional proxy must be able to override this method.
    @Override
    @Transactional
    public UserResponse create(R request, Long actorId) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("An account with this email already exists");
        }
        checkExtraRules(request);
        User actor = (actorId == null) ? null
                : userRepository.findById(actorId)
                        .orElseThrow(() -> new ResourceNotFoundException("Acting user not found: " + actorId));

        U saved = userRepository.save(build(request, email, passwordEncoder.encode(request.password())));

        auditService.record(actor != null ? actor : saved, AuditAction.CREATE, AuditEntityType.USER,
                saved.getId(), auditDetails(saved));
        log.info("Created {} account id={}", saved.getRole(), saved.getId());
        return UserResponse.from(saved);
    }

    /** Builds the concrete account (Patient, Doctor, ...) from the request. Password is already hashed. */
    protected abstract U build(R request, String normalizedEmail, String passwordHash);

    /** Hook for subtype-specific uniqueness/business rules. Default: none. */
    protected void checkExtraRules(R request) { }

    /** Hook for the audit description. Subclasses may override. */
    protected String auditDetails(U account) {
        return "Created " + account.getRole() + " account " + account.getEmail();
    }
}
