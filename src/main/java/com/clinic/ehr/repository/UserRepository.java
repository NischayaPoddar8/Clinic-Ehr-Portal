package com.clinic.ehr.repository;

import com.clinic.ehr.model.User;
import com.clinic.ehr.model.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    @Query("""
           select u from User u
           where (:role is null or u.role = :role)
             and (:active is null or u.active = :active)
           order by u.id asc
           """)
    Page<User> findFiltered(@Param("role") Role role, @Param("active") Boolean active, Pageable pageable);
}
