package com.cloudstorage.dto;

import java.time.Instant;
import java.util.List;

public final class UploadDtos {
    private UploadDtos() {
    }

    public record InitUploadRequest(
            String fileName,
            String contentType,
            long sizeBytes,
            Long parentId,
            Long chunkSize) {
    }

    public record CompleteUploadRequest(String fileSha256) {
    }

    public record ChunkResponse(
            int chunkIndex,
            long sizeBytes,
            String sha256) {
    }

    public record UploadSessionResponse(
            String uploadId,
            Long parentId,
            String fileName,
            String contentType,
            long sizeBytes,
            long chunkSize,
            int totalChunks,
            long uploadedBytes,
            String status,
            Long completedFileId,
            Instant expiresAt,
            List<ChunkResponse> chunks) {
    }
}
