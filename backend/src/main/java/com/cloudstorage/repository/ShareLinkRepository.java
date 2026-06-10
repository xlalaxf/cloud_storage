package com.cloudstorage.repository;

import com.cloudstorage.model.ShareLink;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShareLinkRepository extends JpaRepository<ShareLink, Long> {
    Optional<ShareLink> findByToken(String token);

    List<ShareLink> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
}
