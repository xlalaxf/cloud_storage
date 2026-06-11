package com.cloudstorage.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uk_users_username", columnNames = "username"))
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 120)
    private String reserveEmail;

    @Column(length = 60)
    private String nickname;

    @Column(length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private boolean deleted = false;

    @Column(columnDefinition = "datetime")
    private Instant deletedAt;

    @Column(length = 300)
    private String banReason;

    @Column(columnDefinition = "datetime")
    private Instant bannedUntil;

    @Lob
    @Column(columnDefinition = "MEDIUMBLOB")
    private byte[] avatarData;

    @Column(length = 80)
    private String avatarContentType;

    @CreationTimestamp
    @Column(nullable = false, updatable = false, columnDefinition = "datetime")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false, columnDefinition = "datetime")
    private Instant updatedAt;

    public boolean isBanned() {
        return banReason != null
                && !banReason.isBlank()
                && (bannedUntil == null || bannedUntil.isAfter(Instant.now()));
    }

    public boolean isAccessAllowed() {
        return enabled && !deleted && !isBanned();
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
