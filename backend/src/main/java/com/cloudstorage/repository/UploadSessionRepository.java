package com.cloudstorage.repository;

import com.cloudstorage.model.UploadSession;
import com.cloudstorage.model.UploadStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadSessionRepository extends JpaRepository<UploadSession, Long> {
    Optional<UploadSession> findByUploadIdAndOwnerId(String uploadId, Long ownerId);

    List<UploadSession> findByStatusInAndExpiresAtBefore(List<UploadStatus> statuses, Instant expiresAt);
}
