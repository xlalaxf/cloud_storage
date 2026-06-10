package com.cloudstorage.controller;

import com.cloudstorage.dto.ApiResponse;
import com.cloudstorage.dto.FileDtos.FileResponse;
import com.cloudstorage.dto.UploadDtos.CompleteUploadRequest;
import com.cloudstorage.dto.UploadDtos.InitUploadRequest;
import com.cloudstorage.dto.UploadDtos.UploadSessionResponse;
import com.cloudstorage.model.User;
import com.cloudstorage.service.ChunkedUploadService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files/uploads")
public class UploadController {
    private final ChunkedUploadService uploadService;

    public UploadController(ChunkedUploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping("/init")
    public ApiResponse<UploadSessionResponse> init(
            @AuthenticationPrincipal User user,
            @RequestParam String fileName,
            @RequestParam(required = false) String contentType,
            @RequestParam long sizeBytes,
            @RequestParam(required = false) Long parentId,
            @RequestParam(required = false) Long chunkSize) {
        return ApiResponse.ok("上传任务已创建", uploadService.init(
                user,
                new InitUploadRequest(fileName, contentType, sizeBytes, parentId, chunkSize)));
    }

    @GetMapping("/{uploadId}")
    public ApiResponse<UploadSessionResponse> status(
            @AuthenticationPrincipal User user,
            @PathVariable String uploadId) {
        return ApiResponse.ok(uploadService.status(user, uploadId));
    }

    @PutMapping("/{uploadId}/chunks/{chunkIndex}")
    public ApiResponse<UploadSessionResponse> uploadChunk(
            @AuthenticationPrincipal User user,
            @PathVariable String uploadId,
            @PathVariable int chunkIndex,
            @RequestPart("chunk") MultipartFile chunk) {
        return ApiResponse.ok(uploadService.uploadChunk(user, uploadId, chunkIndex, chunk));
    }

    @PostMapping("/{uploadId}/complete")
    public ApiResponse<FileResponse> complete(
            @AuthenticationPrincipal User user,
            @PathVariable String uploadId,
            @RequestParam(required = false) String fileSha256) {
        return ApiResponse.ok("上传完成", uploadService.complete(user, uploadId, new CompleteUploadRequest(fileSha256)));
    }

    @DeleteMapping("/{uploadId}")
    public ApiResponse<Void> cancel(
            @AuthenticationPrincipal User user,
            @PathVariable String uploadId) {
        uploadService.cancel(user, uploadId);
        return ApiResponse.ok("上传已取消", null);
    }
}
