package com.cloudstorage.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "share_links", indexes = @Index(name = "idx_share_token", columnList = "token", unique = true))
public class ShareLink {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "root_file_id", nullable = false)
    private CloudFile rootFile;

    @Column(nullable = false, length = 80)
    private String token;

    @Column(length = 20)
    private String extractionCode;

    @Column(nullable = false)
    private boolean enabled = true;

    private Instant expiresAt;

    @Column(nullable = false)
    private long downloadCount = 0;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
