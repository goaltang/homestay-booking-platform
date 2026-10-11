package com.homestay3.homestaybackend.repository;

import com.homestay3.homestaybackend.entity.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;

public interface AuthSessionRepository extends JpaRepository<AuthSession, String> {
    void deleteByExpiresAtBefore(Instant now);
}
