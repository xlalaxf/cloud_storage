package com.cloudstorage.service;

import com.cloudstorage.dto.FileDtos.ArchiveJobResponse;
import com.cloudstorage.model.User;
import jakarta.annotation.PreDestroy;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

@Service
public class ArchiveJobService {
    private static final Logger log = LoggerFactory.getLogger(ArchiveJobService.class);
    private static final int MAX_CONCURRENT_JOBS = Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));

    private final FileService fileService;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            MAX_CONCURRENT_JOBS,
            MAX_CONCURRENT_JOBS,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(),
            namedThreadFactory("archive-job-"));
    private final Map<String, ArchiveJob> jobs = new ConcurrentHashMap<>();
    private final Map<String, String> activeByUserFile = new ConcurrentHashMap<>();

    public ArchiveJobService(FileService fileService) {
        this.fileService = fileService;
        executor.prestartAllCoreThreads();
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
        jobs.values().forEach(job -> {
            if (job.archivePath != null) {
                fileService.deleteTempArchive(job.archivePath);
            }
        });
    }

    public ArchiveJobResponse start(User user, Long fileId) {
        String activeKey = activeKey(user.getId(), fileId);
        String existingJobId = activeByUserFile.get(activeKey);
        ArchiveJob existing = existingJobId == null ? null : jobs.get(existingJobId);
        if (existing != null && existing.isRunning()) {
            return toResponse(existing);
        }

        String fileName = fileService.archivableFolderName(user, fileId);
        ArchiveJob job = new ArchiveJob(UUID.randomUUID().toString(), user.getId(), fileId);
        job.queued(fileName);
        jobs.put(job.id, job);
        activeByUserFile.put(activeKey, job.id);
        executor.submit(() -> runJob(job, activeKey));
        log.info(
                "Queued archive job id={} userId={} fileId={} fileName={} activeJobs={} queuedJobs={}",
                job.id,
                user.getId(),
                fileId,
                fileName,
                executor.getActiveCount(),
                executor.getQueue().size());
        return toResponse(job);
    }

    public ArchiveJobResponse status(User user, String jobId) {
        ArchiveJob job = requireJob(user, jobId);
        return toResponse(job);
    }

    public FileService.DownloadPayload download(User user, String jobId) {
        ArchiveJob job = requireJob(user, jobId);
        if (job.status != ArchiveStatus.COMPLETED || job.archivePath == null) {
            throw AppException.badRequest("压缩包尚未准备完成");
        }
        Resource resource = fileService.tempArchiveResource(job.archivePath);
        return new FileService.DownloadPayload(
                resource,
                job.downloadName,
                MediaType.parseMediaType("application/zip"),
                job.archiveSizeBytes);
    }

    private ArchiveJob requireJob(User user, String jobId) {
        ArchiveJob job = jobs.get(jobId);
        if (job == null || !job.userId.equals(user.getId())) {
            throw AppException.notFound("压缩任务不存在");
        }
        return job;
    }

    private void runJob(ArchiveJob job, String activeKey) {
        long startedNanos = System.nanoTime();
        log.info("Starting archive job id={} userId={} fileId={}", job.id, job.userId, job.fileId);
        try {
            job.scanning(job.fileName);
            fileService.createFolderArchiveJob(job);
            log.info(
                    "Completed archive job id={} fileId={} archiveSize={} elapsedMs={}",
                    job.id,
                    job.fileId,
                    job.archiveSizeBytes,
                    Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
        } catch (Exception ex) {
            if (job.archivePath != null) {
                fileService.deleteTempArchive(job.archivePath);
            }
            job.fail(ex.getMessage() == null || ex.getMessage().isBlank() ? "压缩失败" : ex.getMessage());
            log.warn(
                    "Failed archive job id={} fileId={} elapsedMs={} message={}",
                    job.id,
                    job.fileId,
                    Duration.ofNanos(System.nanoTime() - startedNanos).toMillis(),
                    job.message,
                    ex);
        } finally {
            activeByUserFile.remove(activeKey, job.id);
        }
    }

    private String activeKey(Long userId, Long fileId) {
        return userId + ":" + fileId;
    }

    private static ThreadFactory namedThreadFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger(1);
        return runnable -> new Thread(runnable, prefix + sequence.getAndIncrement());
    }

    private ArchiveJobResponse toResponse(ArchiveJob job) {
        return new ArchiveJobResponse(
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
                job.status == ArchiveStatus.COMPLETED ? "/files/archive-jobs/" + job.id + "/download" : null,
                job.downloadName,
                job.archiveSizeBytes,
                job.startedAt,
                job.completedAt);
    }

    public static final class ArchiveJob {
        private final String id;
        private final Long userId;
        private final Long fileId;
        private volatile String fileName = "";
        private volatile String downloadName = "";
        private volatile ArchiveStatus status = ArchiveStatus.PENDING;
        private volatile int processedEntries = 0;
        private volatile int totalEntries = 0;
        private volatile long processedBytes = 0;
        private volatile long totalBytes = 0;
        private volatile String currentEntryName = "";
        private volatile String message = "等待压缩";
        private volatile Path archivePath;
        private volatile long archiveSizeBytes = 0;
        private final Instant startedAt = Instant.now();
        private volatile Instant completedAt;

        ArchiveJob(String id, Long userId, Long fileId) {
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

        public void queued(String fileName) {
            this.fileName = fileName == null ? "" : fileName;
            this.status = ArchiveStatus.PENDING;
            this.message = "排队等待压缩";
        }

        public void scanning(String fileName) {
            this.fileName = fileName == null ? "" : fileName;
            this.status = ArchiveStatus.SCANNING;
            this.message = "正在分析文件夹";
        }

        public void compressing(int totalEntries, long totalBytes) {
            this.status = ArchiveStatus.RUNNING;
            this.totalEntries = totalEntries;
            this.totalBytes = totalBytes;
            this.processedEntries = 0;
            this.processedBytes = 0;
            this.currentEntryName = "";
            this.message = "正在压缩";
        }

        public void advance(int processedEntries, long processedBytes, String currentEntryName) {
            this.processedEntries = processedEntries;
            this.processedBytes = processedBytes;
            this.currentEntryName = currentEntryName == null ? "" : currentEntryName;
        }

        public void complete(Path archivePath, String downloadName, long archiveSizeBytes) {
            this.archivePath = archivePath;
            this.downloadName = downloadName;
            this.archiveSizeBytes = archiveSizeBytes;
            this.status = ArchiveStatus.COMPLETED;
            this.processedEntries = Math.max(this.processedEntries, this.totalEntries);
            this.processedBytes = Math.max(this.processedBytes, this.totalBytes);
            this.currentEntryName = "";
            this.message = "压缩完成";
            this.completedAt = Instant.now();
        }

        public void fail(String message) {
            this.status = ArchiveStatus.FAILED;
            this.message = message;
            this.completedAt = Instant.now();
        }

        public boolean isRunning() {
            return status == ArchiveStatus.PENDING || status == ArchiveStatus.SCANNING || status == ArchiveStatus.RUNNING;
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
            if (status == ArchiveStatus.COMPLETED) {
                return 100;
            }
            if (status == ArchiveStatus.SCANNING) {
                return 1;
            }
            if (totalBytes > 0) {
                return Math.max(1, Math.min(99, (int) Math.floor((processedBytes * 100.0) / totalBytes)));
            }
            if (totalEntries > 0) {
                return Math.max(1, Math.min(99, (int) Math.floor((processedEntries * 100.0) / totalEntries)));
            }
            return status == ArchiveStatus.PENDING ? 0 : 1;
        }
    }

    private enum ArchiveStatus {
        PENDING,
        SCANNING,
        RUNNING,
        COMPLETED,
        FAILED
    }
}
