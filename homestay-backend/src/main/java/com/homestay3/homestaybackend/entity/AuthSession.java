package com.homestay3.homestaybackend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "auth_sessions")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AuthSession {
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "credential_digest", nullable = false, length = 64)
    private String credentialDigest;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
