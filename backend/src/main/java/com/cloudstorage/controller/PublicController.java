package com.cloudstorage.controller;

import com.cloudstorage.dto.ApiResponse;
import com.cloudstorage.dto.FileDtos.PublicShareResponse;
import com.cloudstorage.dto.SystemSettingsDtos.PublicSettingsResponse;
import com.cloudstorage.service.FileService;
import com.cloudstorage.service.LinkService;
import com.cloudstorage.service.SystemSettingsService;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class PublicController {
    private final LinkService linkService;
    private final SystemSettingsService systemSettingsService;

    public PublicController(LinkService linkService, SystemSettingsService systemSettingsService) {
        this.linkService = linkService;
        this.systemSettingsService = systemSettingsService;
    }

    @GetMapping("/settings")
    public ApiResponse<PublicSettingsResponse> settings() {
        return ApiResponse.ok(systemSettingsService.publicSettings());
    }

    @GetMapping("/direct/{token}")
    public ResponseEntity<?> direct(@PathVariable String token) {
        return binary(linkService.downloadDirect(token));
    }

    @GetMapping("/shares/{token}")
    public ApiResponse<PublicShareResponse> share(
            @PathVariable String token,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) Long parentId) {
        return ApiResponse.ok(linkService.viewShare(token, code, parentId));
    }

    @GetMapping("/shares/{token}/files/{fileId}/download")
    public ResponseEntity<?> shareDownload(
            @PathVariable String token,
            @PathVariable Long fileId,
            @RequestParam(required = false) String code) {
        return binary(linkService.downloadShare(token, code, fileId));
    }

    @GetMapping("/shares/{token}/files/{fileId}/preview")
    public ResponseEntity<?> sharePreview(
            @PathVariable String token,
            @PathVariable Long fileId,
            @RequestParam(required = false) String code) {
        return binary(linkService.previewShare(token, code, fileId), false);
    }

    private ResponseEntity<?> binary(FileService.DownloadPayload payload) {
        return binary(payload, true);
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
}
