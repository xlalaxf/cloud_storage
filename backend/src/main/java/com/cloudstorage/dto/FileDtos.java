package com.cloudstorage.dto;

import com.cloudstorage.model.FileKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class FileDtos {
    private FileDtos() {
    }

    public record FileResponse(
            Long id,
            Long ownerId,
            Long parentId,
            FileKind fileKind,
            String name,
            String contentType,
            String extension,
            long sizeBytes,
            long downloadCount,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record CreateFolderRequest(
            @NotBlank @Size(max = 180) String name,
            Long parentId) {
    }

    public record RenameRequest(@NotBlank @Size(max = 180) String name) {
    }

    public record MoveRequest(Long targetParentId) {
    }

    public record BatchFileRequest(List<Long> fileIds, Long targetParentId) {
    }

    public record FolderTreeNode(Long id, Long parentId, String name, List<FolderTreeNode> children) {
    }

    public record CreateDirectLinkRequest(Instant expiresAt) {
    }

    public record DirectLinkResponse(
            Long id,
            Long fileId,
            String token,
            String url,
            boolean enabled,
            Instant expiresAt,
            long downloadCount,
            Instant createdAt) {
    }

    public record CreateShareRequest(String extractionCode, Instant expiresAt) {
    }

    public record ShareLinkResponse(
            Long id,
            Long rootFileId,
            String rootFileName,
            FileKind rootFileKind,
            String token,
            String url,
            boolean requiresCode,
            String extractionCode,
            boolean enabled,
            Instant expiresAt,
            long downloadCount,
            Instant createdAt) {
    }

    public record PublicShareResponse(
            String token,
            boolean requiresCode,
            boolean unlocked,
            FileResponse root,
            List<FileResponse> files,
            long downloadCount) {
    }
}
