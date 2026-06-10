package com.cloudstorage.repository;

import com.cloudstorage.model.LoginAudit;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginAuditRepository extends JpaRepository<LoginAudit, Long> {
    List<LoginAudit> findTop30ByUserIdOrderByCreatedAtDesc(Long userId);

    List<LoginAudit> findTop30ByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long userId,
            Instant from,
            Instant to);
}
