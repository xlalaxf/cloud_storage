package com.cloudstorage.service;

import com.cloudstorage.dto.FileDtos.ExtractJobResponse;
import com.cloudstorage.dto.FileDtos.FileResponse;
import com.cloudstorage.model.User;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

@Service
public class ExtractJobService {
    private static final int MAX_CONCURRENT_JOBS = Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));

    private final FileService fileService;
    private final ExecutorService executor = Executors.newFixedThreadPool(
            MAX_CONCURRENT_JOBS,
            namedThreadFactory("extract-job-"));
    private final Map<String, ExtractJob> jobs = new ConcurrentHashMap<>();
    private final Map<String, String> activeByUserFile = new ConcurrentHashMap<>();

    public ExtractJobService(FileService fileService) {
        this.fileService = fileService;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    public ExtractJobResponse start(User user, Long fileId) {
        String activeKey = activeKey(user.getId(), fileId);
        String existingJobId = activeByUserFile.get(activeKey);
        ExtractJob existing = existingJobId == null ? null : jobs.get(existingJobId);
        if (existing != null && existing.isRunning()) {
            return toResponse(existing);
        }

        fileService.validateExtractable(user, fileId);
        ExtractJob job = new ExtractJob(UUID.randomUUID().toString(), user.getId(), fileId);
        jobs.put(job.id, job);
        activeByUserFile.put(activeKey, job.id);
        executor.submit(() -> runJob(job, activeKey));
        return toResponse(job);
    }

    public ExtractJobResponse status(User user, String jobId) {
        ExtractJob job = jobs.get(jobId);
        if (job == null || !job.userId.equals(user.getId())) {
            throw AppException.notFound("解压任务不存在");
        }
        return toResponse(job);
    }

    private void runJob(ExtractJob job, String activeKey) {
        try {
            fileService.extractZipJob(job);
        } catch (Exception ex) {
            job.fail(ex.getMessage() == null || ex.getMessage().isBlank() ? "解压失败" : ex.getMessage());
        } finally {
            activeByUserFile.remove(activeKey, job.id);
        }
    }

    private String activeKey(Long userId, Long fileId) {
        return userId + ":" + fileId;
    }

    private static ThreadFactory namedThreadFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + sequence.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }

    private ExtractJobResponse toResponse(ExtractJob job) {
        return new ExtractJobResponse(
                job.id,
                job.fileId,
                job.fileName,
                job.status.name(),
                job.percent(),
                job.processedEntries,
                job.totalEntries,
                job.processedBytes,
                job.totalBytes,
                job.currentEntryName,
                job.speedBytesPerSecond(),
                job.elapsedMillis(),
                job.message,
                job.root,
                job.startedAt,
                job.completedAt);
    }

    public static final class ExtractJob {
        private final String id;
        private final Long userId;
        private final Long fileId;
        private volatile String fileName = "";
        private volatile ExtractStatus status = ExtractStatus.PENDING;
        private volatile int processedEntries = 0;
        private volatile int totalEntries = 0;
        private volatile long processedBytes = 0;
        private volatile long totalBytes = 0;
        private volatile String currentEntryName = "";
        private volatile String message = "等待解压";
        private volatile FileResponse root;
        private final Instant startedAt = Instant.now();
        private volatile Instant completedAt;

        ExtractJob(String id, Long userId, Long fileId) {
            this.id = id;
            this.userId = userId;
            this.fileId = fileId;
        }

        public Long userId() {
            return userId;
        }

        public Long fileId() {
            return fileId;
        }

        public void scanning(String fileName) {
            this.fileName = fileName;
            this.status = ExtractStatus.SCANNING;
            this.message = "正在分析压缩包";
        }

        public void extracting(int totalEntries, long totalBytes) {
            this.status = ExtractStatus.RUNNING;
            this.totalEntries = totalEntries;
            this.totalBytes = totalBytes;
            this.processedEntries = 0;
            this.processedBytes = 0;
            this.currentEntryName = "";
            this.message = "正在解压";
        }

        public void advance(int processedEntries, long processedBytes, String currentEntryName) {
            this.processedEntries = processedEntries;
            this.processedBytes = processedBytes;
            this.currentEntryName = currentEntryName == null ? "" : currentEntryName;
        }

        public void complete(FileResponse root) {
            this.root = root;
            this.status = ExtractStatus.COMPLETED;
            this.processedEntries = Math.max(this.processedEntries, this.totalEntries);
            this.processedBytes = Math.max(this.processedBytes, this.totalBytes);
            this.currentEntryName = "";
            this.percent();
            this.message = "解压完成";
            this.completedAt = Instant.now();
        }

        public void fail(String message) {
            this.status = ExtractStatus.FAILED;
            this.message = message;
            this.completedAt = Instant.now();
        }

        public boolean isRunning() {
            return status == ExtractStatus.PENDING || status == ExtractStatus.SCANNING || status == ExtractStatus.RUNNING;
        }

        public long speedBytesPerSecond() {
            long elapsed = Math.max(1, elapsedMillis());
            return Math.max(0, (processedBytes * 1000) / elapsed);
        }

        public long elapsedMillis() {
            Instant end = completedAt == null ? Instant.now() : completedAt;
            return Math.max(0, end.toEpochMilli() - startedAt.toEpochMilli());
        }

        public int percent() {
            if (status == ExtractStatus.COMPLETED) {
                return 100;
            }
            if (status == ExtractStatus.SCANNING) {
                return 1;
            }
            if (totalEntries > 0) {
                return Math.max(1, Math.min(99, (int) Math.floor((processedEntries * 100.0) / totalEntries)));
            }
            if (totalBytes > 0) {
                return Math.max(1, Math.min(99, (int) Math.floor((processedBytes * 100.0) / totalBytes)));
            }
            return status == ExtractStatus.PENDING ? 0 : 1;
        }
    }

    private enum ExtractStatus {
        PENDING,
        SCANNING,
        RUNNING,
        COMPLETED,
        FAILED
    }
}
