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
@Table(
        name = "file_operation_audits",
        indexes = {
            @Index(name = "idx_file_ops_user_created", columnList = "user_id,created_at"),
            @Index(name = "idx_file_ops_file_created", columnList = "file_id,created_at")
        })
public class FileOperationAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "user_id", insertable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(name = "file_id")
    private Long fileId;

    @Column(length = 180)
    private String fileName;

    @Column(nullable = false, length = 40)
    private String operation;

    @Column(length = 500)
    private String detail;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
