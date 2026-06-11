package com.cloudstorage.dto;

import com.cloudstorage.model.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class AdminDtos {
    private AdminDtos() {
    }

    public record AdminUserResponse(
            Long id,
            String username,
            String nickname,
            String reserveEmail,
            Role role,
            boolean enabled,
            boolean deleted,
            Instant deletedAt,
            boolean banned,
            String banReason,
            Instant bannedUntil,
            Instant createdAt) {
    }

    public record BanUserRequest(
            @NotBlank @Size(max = 300) String reason,
            Instant bannedUntil) {
    }

    public record LoginAuditResponse(
            Long id,
            String username,
            String ipAddress,
            boolean successful,
            String message,
            Instant createdAt) {
    }

    public record FileOperationAuditResponse(
            Long id,
            Long userId,
            String username,
            Long fileId,
            String fileName,
            String operation,
            String detail,
            Instant createdAt) {
    }

    public record AuditClearResponse(
            long loginAuditCount,
            long fileOperationAuditCount) {
    }

    public record StorageCleanupResponse(
            long expiredUploadSessions,
            long expiredUploadChunks,
            long expiredUploadBytes,
            long deletedTemporaryFiles,
            long deletedTemporaryBytes,
            long failedTemporaryFiles,
            long releasedBytes) {
    }

    public record OrphanStorageCleanupResponse(
            long scannedObjectFiles,
            long deletedObjectFiles,
            long deletedObjectBytes,
            long failedObjectFiles,
            long releasedBytes) {
    }
}
