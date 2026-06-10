package com.cloudstorage.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "upload_sessions",
        indexes = {
            @Index(name = "idx_upload_sessions_upload_id", columnList = "upload_id", unique = true),
            @Index(name = "idx_upload_sessions_owner_status", columnList = "owner_id,status"),
            @Index(name = "idx_upload_sessions_expires_at", columnList = "expires_at")
        })
public class UploadSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "upload_id", nullable = false, length = 60)
    private String uploadId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private CloudFile parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_file_id")
    private CloudFile completedFile;

    @Column(nullable = false, length = 180)
    private String fileName;

    @Column(length = 120)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private long chunkSize;

    @Column(nullable = false)
    private int totalChunks;

    @Column(nullable = false)
    private long uploadedBytes = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UploadStatus status = UploadStatus.UPLOADING;

    @Column(length = 64)
    private String fileSha256;

    @Column(nullable = false)
    private Instant expiresAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
