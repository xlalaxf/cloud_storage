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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "upload_chunks",
        indexes = @Index(name = "idx_upload_chunks_session", columnList = "upload_session_id"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_upload_chunks_session_index",
                columnNames = {"upload_session_id", "chunk_index"}))
public class UploadChunk {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "upload_session_id", nullable = false)
    private UploadSession session;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(nullable = false, length = 500)
    private String relativePath;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
