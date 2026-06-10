package com.cloudstorage.repository;

import com.cloudstorage.model.FileOperationAudit;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileOperationAuditRepository extends JpaRepository<FileOperationAudit, Long> {
    List<FileOperationAudit> findTop80ByUserIdOrderByCreatedAtDesc(Long userId);

    List<FileOperationAudit> findTop80ByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long userId,
            Instant from,
            Instant to);
}
