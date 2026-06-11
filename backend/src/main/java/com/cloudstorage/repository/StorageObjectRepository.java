package com.cloudstorage.repository;

import com.cloudstorage.model.StorageObject;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StorageObjectRepository extends JpaRepository<StorageObject, Long> {
    Optional<StorageObject> findFirstBySha256AndSizeBytes(String sha256, long sizeBytes);

    List<StorageObject> findBySha256In(Collection<String> sha256s);

    @Query("select storageObject.relativePath from StorageObject storageObject")
    List<String> findAllRelativePaths();
}
