package com.cloudstorage.controller;

import com.cloudstorage.dto.AdminDtos.AdminUserResponse;
import com.cloudstorage.dto.AdminDtos.AuditClearResponse;
import com.cloudstorage.dto.AdminDtos.BanUserRequest;
import com.cloudstorage.dto.AdminDtos.FileOperationAuditResponse;
import com.cloudstorage.dto.AdminDtos.LoginAuditResponse;
import com.cloudstorage.dto.ApiResponse;
import com.cloudstorage.dto.FileDtos.FileResponse;
import com.cloudstorage.dto.SystemSettingsDtos.AdminSettingsResponse;
import com.cloudstorage.dto.SystemSettingsDtos.UpdateSystemSettingsRequest;
import com.cloudstorage.model.User;
import com.cloudstorage.service.AdminUserService;
import com.cloudstorage.service.AuditService;
import com.cloudstorage.service.FileService;
import com.cloudstorage.service.SystemSettingsService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminUserService adminUserService;
    private final AuditService auditService;
    private final FileService fileService;
    private final SystemSettingsService systemSettingsService;

    public AdminController(
            AdminUserService adminUserService,
            AuditService auditService,
            FileService fileService,
            SystemSettingsService systemSettingsService) {
        this.adminUserService = adminUserService;
        this.auditService = auditService;
        this.fileService = fileService;
        this.systemSettingsService = systemSettingsService;
    }

    @GetMapping("/settings")
    public ApiResponse<AdminSettingsResponse> settings() {
        return ApiResponse.ok(systemSettingsService.adminSettings());
    }

    @PutMapping("/settings")
    public ApiResponse<AdminSettingsResponse> updateSettings(
            @Valid @RequestBody UpdateSystemSettingsRequest request) {
        return ApiResponse.ok("系统设置已保存", systemSettingsService.update(request));
    }

    @GetMapping("/users")
    public ApiResponse<List<AdminUserResponse>> users() {
        return ApiResponse.ok(adminUserService.listUsers());
    }

    @PostMapping("/users/{userId}/ban")
    public ApiResponse<AdminUserResponse> banUser(
            @AuthenticationPrincipal User admin,
            @PathVariable Long userId,
            @Valid @RequestBody BanUserRequest request) {
        return ApiResponse.ok("已封禁", adminUserService.banUser(admin, userId, request));
    }

    @PostMapping("/users/{userId}/unban")
    public ApiResponse<AdminUserResponse> unbanUser(@PathVariable Long userId) {
        return ApiResponse.ok("已解封", adminUserService.unbanUser(userId));
    }

    @DeleteMapping("/users/{userId}")
    public ApiResponse<Void> deleteUser(@AuthenticationPrincipal User admin, @PathVariable Long userId) {
        adminUserService.deleteUser(admin, userId);
        return ApiResponse.ok("已删除用户", null);
    }

    @GetMapping("/users/{userId}/login-audits")
    public ApiResponse<List<LoginAuditResponse>> loginAudits(
            @PathVariable Long userId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return ApiResponse.ok(auditService.listLoginAudits(userId, from, to));
    }

    @GetMapping("/users/{userId}/file-operation-audits")
    public ApiResponse<List<FileOperationAuditResponse>> fileOperationAudits(
            @PathVariable Long userId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return ApiResponse.ok(auditService.listFileOperationAudits(userId, from, to));
    }

    @DeleteMapping("/audits")
    public ApiResponse<AuditClearResponse> clearAudits() {
        return ApiResponse.ok("已清除所有记录", auditService.clearAllAudits());
    }

    @GetMapping("/users/{userId}/files")
    public ApiResponse<List<FileResponse>> userFiles(
            @PathVariable Long userId,
            @RequestParam(required = false) Long parentId) {
        return ApiResponse.ok(fileService.listForAdmin(userId, parentId));
    }

    @DeleteMapping("/files/{fileId}")
    public ApiResponse<Void> deleteFile(@AuthenticationPrincipal User admin, @PathVariable Long fileId) {
        fileService.deleteAsAdmin(admin, fileId);
        return ApiResponse.ok("已删除", null);
    }
}
