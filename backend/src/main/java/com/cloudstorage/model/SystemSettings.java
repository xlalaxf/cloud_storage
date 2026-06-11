package com.cloudstorage.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
@Table(name = "system_settings")
public class SystemSettings {
    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    @Column(nullable = false, length = 80)
    private String siteName = "Cloud Storage";

    @Column(nullable = false)
    private boolean allowUserLogin = true;

    @Column(nullable = false)
    private boolean allowUserRegistration = true;

    @Column(nullable = false)
    private boolean allowAvatarChange = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false, columnDefinition = "datetime")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false, columnDefinition = "datetime")
    private Instant updatedAt;
}
