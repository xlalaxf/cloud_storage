package com.cloudstorage.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class UploadCleanupTask {
    private final ChunkedUploadService uploadService;

    public UploadCleanupTask(ChunkedUploadService uploadService) {
        this.uploadService = uploadService;
    }

    @Scheduled(fixedDelayString = "${app.upload.cleanup-delay-ms:3600000}")
    public void cleanupExpiredUploads() {
        uploadService.cleanupExpired();
    }
}
