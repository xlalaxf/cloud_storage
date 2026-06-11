package com.cloudstorage.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
        name = "storage_objects",
        indexes = {
            @Index(name = "idx_storage_objects_sha_size", columnList = "sha256,size_bytes"),
            @Index(name = "idx_storage_objects_ref_count", columnList = "ref_count")
        })
public class StorageObject {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 220)
    private String storedName;

    @Column(nullable = false, length = 500)
    private String relativePath;

    @Column(nullable = false, length = 120)
    private String contentType;

    @Column(length = 30)
    private String extension;

    @Column(nullable = false)
    private long refCount = 0;

    @CreationTimestamp
    @Column(nullable = false, updatable = false, columnDefinition = "datetime")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false, columnDefinition = "datetime")
    private Instant updatedAt;
}
