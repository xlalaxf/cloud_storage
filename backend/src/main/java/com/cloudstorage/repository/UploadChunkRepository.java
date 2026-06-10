package com.cloudstorage.repository;

import com.cloudstorage.model.UploadChunk;
import com.cloudstorage.model.UploadSession;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadChunkRepository extends JpaRepository<UploadChunk, Long> {
    List<UploadChunk> findBySessionOrderByChunkIndexAsc(UploadSession session);

    Optional<UploadChunk> findBySessionAndChunkIndex(UploadSession session, int chunkIndex);

    boolean existsBySessionAndChunkIndex(UploadSession session, int chunkIndex);

    long countBySession(UploadSession session);

    void deleteBySession(UploadSession session);
}
