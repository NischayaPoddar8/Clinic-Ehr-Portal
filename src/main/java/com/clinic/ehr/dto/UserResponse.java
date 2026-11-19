package com.clinic.ehr.dto;

import com.clinic.ehr.model.User;
import com.clinic.ehr.model.enums.Role;

/** Public view of an account. Has no password field, by design. */
public record UserResponse(Long id, String email, String fullName, String displayName,
                           String phone, Role role, boolean active) {

    public static UserResponse from(User user) {
        // getDisplayName() is polymorphic: "Dr. ..." for doctors.
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getDisplayName(),
                user.getPhone(), user.getRole(), user.isActive());
    }
}
