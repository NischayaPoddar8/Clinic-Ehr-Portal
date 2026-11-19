package com.clinic.ehr.service;

import com.clinic.ehr.dto.UserResponse;
import com.clinic.ehr.exception.InvalidBookingException;
import com.clinic.ehr.exception.ResourceNotFoundException;
import com.clinic.ehr.model.User;
import com.clinic.ehr.model.enums.AuditAction;
import com.clinic.ehr.model.enums.AuditEntityType;
import com.clinic.ehr.model.enums.Role;
import com.clinic.ehr.repository.UserRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final AuditService auditService;

    public UserService(UserRepository userRepository, AuditService auditService) {
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        return userRepository.findById(id)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers(Role role, Boolean active, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return userRepository.findFiltered(role, active, PageRequest.of(safePage, safeSize))
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional
    public UserResponse updateStatus(Long userId, boolean active, Long adminId) {
        if (userId.equals(adminId) && !active) {
            throw new InvalidBookingException("Admins cannot deactivate their own account");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found: " + adminId));

        user.setActive(active);
        auditService.record(admin, AuditAction.UPDATE, AuditEntityType.USER, userId,
                (active ? "Activated" : "Deactivated") + " user " + user.getEmail());
        log.info("User {} {} by admin {}", userId, active ? "activated" : "deactivated", adminId);
        return UserResponse.from(user);
    }
}
