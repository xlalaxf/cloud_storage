package com.cloudstorage.repository;

import com.cloudstorage.model.DirectLink;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DirectLinkRepository extends JpaRepository<DirectLink, Long> {
    Optional<DirectLink> findByToken(String token);

    List<DirectLink> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
}
