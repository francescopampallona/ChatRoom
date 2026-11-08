package com.francescopampallona.chatroom.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    // Null for existing human accounts; stable keys identify seeded AI accounts.
    @Column(name = "agent_key", unique = true, length = 40)
    private String agentKey;

    @Transient
    public boolean isAgent() {
        return agentKey != null;
    }

    @CreationTimestamp
    private LocalDateTime createdAt;
}
