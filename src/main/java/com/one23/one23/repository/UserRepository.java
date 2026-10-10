package com.one23.one23.repository;

import com.one23.one23.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// Repository for user database operations
public interface UserRepository extends JpaRepository<User, Long> {

    // Used during login and JWT validation
    Optional<User> findByEmail(String email);

    // Used by signup and login: "Ravi@Gmail.com" and "ravi@gmail.com" are the same account.
    // Ignore-case lookup also works for older users whose email was saved with capitals.
    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByEmailIgnoreCase(String email);
}
