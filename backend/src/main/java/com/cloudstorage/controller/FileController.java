package com.cloudstorage.controller;

import com.cloudstorage.dto.ApiResponse;
import com.cloudstorage.dto.FileDtos.BatchFileRequest;
import com.cloudstorage.dto.FileDtos.CreateDirectLinkRequest;
import com.cloudstorage.dto.FileDtos.CreateFolderRequest;
import com.cloudstorage.dto.FileDtos.CreateShareRequest;
import com.cloudstorage.dto.FileDtos.DirectLinkResponse;
import com.cloudstorage.dto.FileDtos.ExtractJobResponse;
import com.cloudstorage.dto.FileDtos.FileResponse;
import com.cloudstorage.dto.FileDtos.FolderTreeNode;
import com.cloudstorage.dto.FileDtos.MoveRequest;
import com.cloudstorage.dto.FileDtos.RenameRequest;
import com.cloudstorage.dto.FileDtos.ShareLinkResponse;
import com.cloudstorage.model.User;
import com.cloudstorage.service.ExtractJobService;
import com.cloudstorage.service.FileService;
import com.cloudstorage.service.LinkService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final FileService fileService;
    private final LinkService linkService;
    private final ExtractJobService extractJobService;

    public FileController(FileService fileService, LinkService linkService, ExtractJobService extractJobService) {
        this.fileService = fileService;
        this.linkService = linkService;
        this.extractJobService = extractJobService;
    }

    @GetMapping
    public ApiResponse<List<FileResponse>> list(@AuthenticationPrincipal User user, @RequestParam(required = false) Long parentId) {
        return ApiResponse.ok(fileService.list(user, parentId));
    }

    @PostMapping("/folders")
    public ApiResponse<FileResponse> createFolder(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateFolderRequest request) {
        return ApiResponse.ok("文件夹已创建", fileService.createFolder(user, request));
    }

    @PostMapping("/upload")
    public ApiResponse<List<FileResponse>> upload(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Long parentId,
            @RequestParam("files") MultipartFile[] files) {
        return ApiResponse.ok("上传完成", fileService.upload(user, parentId, Arrays.asList(files)));
    }

    @GetMapping("/folders/tree")
    public ApiResponse<List<FolderTreeNode>> folderTree(@AuthenticationPrincipal User user) {
        return ApiResponse.ok(fileService.folderTree(user));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<?> download(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return binary(fileService.downloadOwned(user, id, true), true);
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<?> preview(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return binary(fileService.downloadOwned(user, id, false), false);
    }

    @PatchMapping("/{id}/rename")
    public ApiResponse<FileResponse> rename(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody RenameRequest request) {
        return ApiResponse.ok("已重命名", fileService.rename(user, id, request));
    }

    @PatchMapping("/{id}/move")
    public ApiResponse<FileResponse> move(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestBody MoveRequest request) {
        return ApiResponse.ok("已移动", fileService.move(user, id, request));
    }

    @PostMapping("/{id}/copy")
    public ApiResponse<FileResponse> copy(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestBody MoveRequest request) {
        return ApiResponse.ok("已复制", fileService.copy(user, id, request));
    }

    @PatchMapping("/batch/move")
    public ApiResponse<List<FileResponse>> moveBatch(
            @AuthenticationPrincipal User user,
            @RequestBody BatchFileRequest request) {
        return ApiResponse.ok("已移动", fileService.moveBatch(user, request.fileIds(), request.targetParentId()));
    }

    @PostMapping("/batch/copy")
    public ApiResponse<List<FileResponse>> copyBatch(
            @AuthenticationPrincipal User user,
            @RequestBody BatchFileRequest request) {
        return ApiResponse.ok("已复制", fileService.copyBatch(user, request.fileIds(), request.targetParentId()));
    }

    @PostMapping("/{id}/extract")
    public ApiResponse<ExtractJobResponse> extract(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return ApiResponse.ok("解压任务已开始", extractJobService.start(user, id));
    }

    @GetMapping("/extract-jobs/{jobId}")
    public ApiResponse<ExtractJobResponse> extractStatus(@AuthenticationPrincipal User user, @PathVariable String jobId) {
        return ApiResponse.ok(extractJobService.status(user, jobId));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        fileService.delete(user, id);
        return ApiResponse.ok("已删除", null);
    }

    @DeleteMapping("/batch")
    public ApiResponse<Void> deleteBatch(@AuthenticationPrincipal User user, @RequestBody BatchFileRequest request) {
        fileService.deleteBatch(user, request.fileIds());
        return ApiResponse.ok("已删除", null);
    }

    @PostMapping("/{id}/direct-links")
    public ApiResponse<DirectLinkResponse> createDirectLink(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestBody(required = false) CreateDirectLinkRequest request,
            HttpServletRequest servletRequest) {
        return ApiResponse.ok("直链已创建", linkService.createDirectLink(
                user,
                id,
                request == null ? null : request.expiresAt(),
                linkOrigin(servletRequest)));
    }

    @GetMapping("/direct-links")
    public ApiResponse<List<DirectLinkResponse>> directLinks(
            @AuthenticationPrincipal User user,
            HttpServletRequest servletRequest) {
        return ApiResponse.ok(linkService.listDirectLinks(user, linkOrigin(servletRequest)));
    }

    @DeleteMapping("/direct-links/{linkId}")
    public ApiResponse<Void> deleteDirectLink(@AuthenticationPrincipal User user, @PathVariable Long linkId) {
        linkService.deleteDirectLink(user, linkId);
        return ApiResponse.ok("直链已删除", null);
    }

    @PostMapping("/{id}/shares")
    public ApiResponse<ShareLinkResponse> createShare(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestBody(required = false) CreateShareRequest request,
            HttpServletRequest servletRequest) {
        return ApiResponse.ok("分享已创建", linkService.createShare(
                user,
                id,
                request == null ? null : request.extractionCode(),
                request == null ? null : request.expiresAt(),
                linkOrigin(servletRequest)));
    }

    @GetMapping("/shares")
    public ApiResponse<List<ShareLinkResponse>> shares(
            @AuthenticationPrincipal User user,
            HttpServletRequest servletRequest) {
        return ApiResponse.ok(linkService.listShares(user, linkOrigin(servletRequest)));
    }

    @DeleteMapping("/shares/{shareId}")
    public ApiResponse<Void> deleteShare(@AuthenticationPrincipal User user, @PathVariable Long shareId) {
        linkService.deleteShare(user, shareId);
        return ApiResponse.ok("分享已删除", null);
    }

    private ResponseEntity<?> binary(FileService.DownloadPayload payload, boolean attachment) {
        ContentDisposition disposition = (attachment ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(payload.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(payload.mediaType())
                .contentLength(payload.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(payload.resource());
    }

    private String linkOrigin(HttpServletRequest request) {
        String origin = normalizeOrigin(request.getHeader(HttpHeaders.ORIGIN));
        if (origin != null) {
            return origin;
        }
        return normalizeOrigin(request.getHeader(HttpHeaders.REFERER));
    }

    private String normalizeOrigin(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim())) {
            return null;
        }
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            String authority = uri.getRawAuthority();
            if (scheme == null || authority == null
                    || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                return null;
            }
            return scheme.toLowerCase() + "://" + authority;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
