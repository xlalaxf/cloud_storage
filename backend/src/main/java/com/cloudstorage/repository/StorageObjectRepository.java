package com.cloudstorage.repository;

import com.cloudstorage.model.StorageObject;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StorageObjectRepository extends JpaRepository<StorageObject, Long> {
    Optional<StorageObject> findFirstBySha256AndSizeBytes(String sha256, long sizeBytes);
}
