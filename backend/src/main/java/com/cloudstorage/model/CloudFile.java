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
        name = "files",
        indexes = {
            @Index(name = "idx_files_owner_parent", columnList = "owner_id,parent_id"),
            @Index(name = "idx_files_owner_parent_name_deleted", columnList = "owner_id,parent_id,name,deleted"),
            @Index(name = "idx_files_deleted", columnList = "deleted")
        })
public class CloudFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private CloudFile parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "object_id")
    private StorageObject object;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FileKind fileKind;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(length = 220)
    private String storedName;

    @Column(length = 500)
    private String relativePath;

    @Column(length = 120)
    private String contentType;

    @Column(length = 30)
    private String extension;

    @Column(nullable = false)
    private long sizeBytes = 0;

    @Column(nullable = false)
    private long downloadCount = 0;

    @Column(nullable = false)
    private boolean deleted = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false, columnDefinition = "datetime")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false, columnDefinition = "datetime")
    private Instant updatedAt;
}
