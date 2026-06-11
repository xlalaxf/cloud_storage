package com.cloudstorage.service;

import com.cloudstorage.dto.AdminDtos.OrphanStorageCleanupResponse;
import com.cloudstorage.dto.AdminDtos.StorageCleanupResponse;
import com.cloudstorage.dto.FileDtos.FileResponse;
import com.cloudstorage.dto.UploadDtos.ChunkResponse;
import com.cloudstorage.dto.UploadDtos.CompleteUploadRequest;
import com.cloudstorage.dto.UploadDtos.InitUploadRequest;
import com.cloudstorage.dto.UploadDtos.UploadSessionResponse;
import com.cloudstorage.model.CloudFile;
import com.cloudstorage.model.FileKind;
import com.cloudstorage.model.StorageObject;
import com.cloudstorage.model.UploadChunk;
import com.cloudstorage.model.UploadSession;
import com.cloudstorage.model.UploadStatus;
import com.cloudstorage.model.User;
import com.cloudstorage.repository.CloudFileRepository;
import com.cloudstorage.repository.StorageObjectRepository;
import com.cloudstorage.repository.UploadChunkRepository;
import com.cloudstorage.repository.UploadSessionRepository;
import com.cloudstorage.repository.UserRepository;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Vector;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ChunkedUploadService {
    private static final long DEFAULT_CHUNK_SIZE = 32L * 1024 * 1024;
    private static final long MIN_CHUNK_SIZE = 1024L * 1024;
    private static final long MAX_CHUNK_SIZE = 128L * 1024 * 1024;
    private static final int MAX_TOTAL_CHUNKS = 10000;

    private final UploadSessionRepository sessionRepository;
    private final UploadChunkRepository chunkRepository;
    private final CloudFileRepository fileRepository;
    private final StorageObjectRepository objectRepository;
    private final UserRepository userRepository;
    private final FileStorageService storageService;
    private final FileMapper fileMapper;
    private final AuditService auditService;
    private final long sessionTtlHours;

    public ChunkedUploadService(
            UploadSessionRepository sessionRepository,
            UploadChunkRepository chunkRepository,
            CloudFileRepository fileRepository,
            StorageObjectRepository objectRepository,
            UserRepository userRepository,
            FileStorageService storageService,
            FileMapper fileMapper,
            AuditService auditService,
            @Value("${app.upload.session-ttl-hours:72}") long sessionTtlHours) {
        this.sessionRepository = sessionRepository;
        this.chunkRepository = chunkRepository;
        this.fileRepository = fileRepository;
        this.objectRepository = objectRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.fileMapper = fileMapper;
        this.auditService = auditService;
        this.sessionTtlHours = sessionTtlHours;
    }

    @Transactional
    public UploadSessionResponse init(User user, InitUploadRequest request) {
        String safeName = storageService.sanitizeName(request.fileName());
        long sizeBytes = request.sizeBytes();
        if (sizeBytes <= 0) {
            throw AppException.badRequest("文件大小不合法");
        }
        long chunkSize = normalizeChunkSize(request.chunkSize());
        int totalChunks = Math.toIntExact((sizeBytes + chunkSize - 1) / chunkSize);
        if (totalChunks <= 0 || totalChunks > MAX_TOTAL_CHUNKS) {
            throw AppException.badRequest("分片数量超过限制");
        }
        CloudFile parent = request.parentId() == null ? null : requireFolder(user, request.parentId());
        UploadSession session = new UploadSession();
        session.setUploadId(UUID.randomUUID().toString().replace("-", ""));
        session.setOwner(userRepository.getReferenceById(user.getId()));
        session.setParent(parent);
        session.setFileName(safeName);
        session.setContentType(cleanContentType(request.contentType(), safeName));
        session.setSizeBytes(sizeBytes);
        session.setChunkSize(chunkSize);
        session.setTotalChunks(totalChunks);
        session.setExpiresAt(Instant.now().plus(Math.max(1, sessionTtlHours), ChronoUnit.HOURS));
        return toResponse(sessionRepository.save(session), List.of());
    }

    @Transactional(readOnly = true)
    public UploadSessionResponse status(User user, String uploadId) {
        UploadSession session = requireSession(user, uploadId);
        return toResponse(session, chunks(session));
    }

    @Transactional
    public UploadSessionResponse uploadChunk(User user, String uploadId, int chunkIndex, MultipartFile file) {
        UploadSession session = requireSession(user, uploadId);
        ensureUploading(session);
        validateChunkIndex(session, chunkIndex);
        if (file == null || file.isEmpty()) {
            throw AppException.badRequest("分片为空");
        }
        long expectedSize = expectedChunkSize(session, chunkIndex);
        if (file.getSize() != expectedSize) {
            throw AppException.badRequest("分片大小不匹配");
        }
        chunkRepository.findBySessionAndChunkIndex(session, chunkIndex).ifPresent(existing -> {
            if (existing.getSizeBytes() == expectedSize) {
                throw AppException.conflict("分片已上传");
            }
            storageService.deleteObject(existing.getRelativePath());
            chunkRepository.delete(existing);
        });
        FileStorageService.StoredChunk stored;
        try (InputStream inputStream = file.getInputStream()) {
            stored = storageService.storeUploadChunk(session.getUploadId(), chunkIndex, inputStream);
        } catch (IOException ex) {
            throw AppException.badRequest("分片读取失败");
        }
        UploadChunk chunk = new UploadChunk();
        chunk.setSession(session);
        chunk.setChunkIndex(chunkIndex);
        chunk.setSizeBytes(stored.size());
        chunk.setSha256(stored.sha256());
        chunk.setRelativePath(stored.relativePath());
        try {
            chunkRepository.saveAndFlush(chunk);
        } catch (DataIntegrityViolationException ex) {
            storageService.deleteObject(stored.relativePath());
            throw AppException.conflict("分片已上传");
        }
        return toResponse(session, chunks(session));
    }

    @Transactional
    public FileResponse complete(User user, String uploadId, CompleteUploadRequest request) {
        UploadSession session = requireSession(user, uploadId);
        ensureUploading(session);
        session.setStatus(UploadStatus.COMPLETING);
        List<UploadChunk> chunks = chunks(session);
        ensureComplete(session, chunks);
        String uniqueName = uniqueName(
                user.getId(),
                session.getParent() == null ? null : session.getParent().getId(),
                session.getFileName());
        FileStorageService.StoredObject stored;
        try (InputStream inputStream = combinedInputStream(chunks)) {
            stored = storageService.prepareObject(inputStream, uniqueName, session.getContentType());
        } catch (IOException ex) {
            session.setStatus(UploadStatus.FAILED);
            throw AppException.badRequest("文件合并失败");
        }
        if (request != null && request.fileSha256() != null && !request.fileSha256().isBlank()
                && !stored.sha256().equalsIgnoreCase(request.fileSha256().trim())) {
            session.setStatus(UploadStatus.FAILED);
            throw AppException.badRequest("文件校验失败");
        }
        StorageObject object = resolveObject(stored);
        CloudFile cloudFile = new CloudFile();
        cloudFile.setOwner(userRepository.getReferenceById(user.getId()));
        cloudFile.setParent(session.getParent());
        cloudFile.setFileKind(FileKind.FILE);
        cloudFile.setName(uniqueName);
        cloudFile.setObject(object);
        cloudFile.setStoredName(object.getStoredName());
        cloudFile.setRelativePath(object.getRelativePath());
        cloudFile.setContentType(object.getContentType());
        cloudFile.setExtension(object.getExtension());
        cloudFile.setSizeBytes(object.getSizeBytes());
        CloudFile saved = fileRepository.save(cloudFile);
        session.setCompletedFile(saved);
        session.setFileSha256(stored.sha256());
        session.setStatus(UploadStatus.COMPLETED);
        auditService.recordFileOperation(user, saved, "UPLOAD", "分片上传; 大小: " + object.getSizeBytes() + " bytes");
        cleanupChunks(session);
        return fileMapper.toResponse(saved);
    }

    @Transactional
    public void cancel(User user, String uploadId) {
        UploadSession session = requireSession(user, uploadId);
        if (session.getStatus() == UploadStatus.COMPLETED) {
            throw AppException.badRequest("上传已完成");
        }
        session.setStatus(UploadStatus.CANCELED);
        cleanupChunks(session);
    }

    @Transactional
    public void cleanupExpired() {
        cleanupExpiredStorage();
    }

    @Transactional
    public StorageCleanupResponse cleanupExpiredStorage() {
        List<UploadStatus> statuses = List.of(UploadStatus.UPLOADING, UploadStatus.FAILED, UploadStatus.CANCELED);
        long expiredUploadSessions = 0;
        long expiredUploadChunks = 0;
        long expiredUploadBytes = 0;
        for (UploadSession session : sessionRepository.findByStatusInAndExpiresAtBefore(statuses, Instant.now())) {
            FileStorageService.DeletedDirectory deleted = cleanupChunksWithStats(session);
            expiredUploadSessions++;
            expiredUploadChunks += deleted.files();
            expiredUploadBytes += deleted.bytes();
        }
        FileStorageService.TemporaryCleanupResult temporary =
                storageService.cleanupExpiredTemporaryObjects(Duration.ofHours(1));
        return new StorageCleanupResponse(
                expiredUploadSessions,
                expiredUploadChunks,
                expiredUploadBytes,
                temporary.files(),
                temporary.bytes(),
                temporary.failures(),
                expiredUploadBytes + temporary.bytes());
    }

    @Transactional(readOnly = true)
    public OrphanStorageCleanupResponse cleanupOrphanStorageObjects() {
        Set<String> referencedPaths = objectRepository.findAllRelativePaths().stream()
                .filter(path -> path != null && !path.isBlank())
                .collect(Collectors.toSet());
        referencedPaths.addAll(fileRepository.findActiveFileRelativePaths().stream()
                .filter(path -> path != null && !path.isBlank())
                .collect(Collectors.toSet()));
        FileStorageService.OrphanObjectCleanupResult result = storageService.cleanupOrphanObjects(referencedPaths);
        return new OrphanStorageCleanupResponse(
                result.scannedFiles(),
                result.files(),
                result.bytes(),
                result.failures(),
                result.bytes());
    }

    private UploadSession requireSession(User user, String uploadId) {
        return sessionRepository.findByUploadIdAndOwnerId(uploadId, user.getId())
                .orElseThrow(() -> AppException.notFound("上传任务不存在"));
    }

    private void ensureUploading(UploadSession session) {
        if (session.getExpiresAt().isBefore(Instant.now())) {
            session.setStatus(UploadStatus.FAILED);
            throw AppException.forbidden("上传任务已过期");
        }
        if (session.getStatus() != UploadStatus.UPLOADING) {
            throw AppException.badRequest("上传任务状态不可操作");
        }
    }

    private void validateChunkIndex(UploadSession session, int chunkIndex) {
        if (chunkIndex < 0 || chunkIndex >= session.getTotalChunks()) {
            throw AppException.badRequest("分片序号不合法");
        }
    }

    private void ensureComplete(UploadSession session, List<UploadChunk> chunks) {
        if (chunks.size() != session.getTotalChunks()) {
            session.setStatus(UploadStatus.UPLOADING);
            throw AppException.badRequest("分片未上传完整");
        }
        long totalSize = 0;
        for (int index = 0; index < chunks.size(); index++) {
            UploadChunk chunk = chunks.get(index);
            if (chunk.getChunkIndex() != index || chunk.getSizeBytes() != expectedChunkSize(session, index)) {
                session.setStatus(UploadStatus.UPLOADING);
                throw AppException.badRequest("分片校验失败");
            }
            totalSize += chunk.getSizeBytes();
        }
        if (totalSize != session.getSizeBytes()) {
            session.setStatus(UploadStatus.UPLOADING);
            throw AppException.badRequest("文件大小校验失败");
        }
    }

    private InputStream combinedInputStream(List<UploadChunk> chunks) throws IOException {
        Vector<InputStream> streams = new Vector<>();
        try {
            for (UploadChunk chunk : chunks) {
                Resource resource = storageService.resource(chunk.getRelativePath());
                streams.add(resource.getInputStream());
            }
            return new SequenceInputStream(streams.elements());
        } catch (IOException | RuntimeException ex) {
            for (InputStream stream : streams) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                    // Best effort cleanup for partially opened chunk streams.
                }
            }
            throw ex;
        }
    }

    private List<UploadChunk> chunks(UploadSession session) {
        return chunkRepository.findBySessionOrderByChunkIndexAsc(session);
    }

    private UploadSessionResponse toResponse(UploadSession session, List<UploadChunk> chunks) {
        return new UploadSessionResponse(
                session.getUploadId(),
                session.getParent() == null ? null : session.getParent().getId(),
                session.getFileName(),
                session.getContentType(),
                session.getSizeBytes(),
                session.getChunkSize(),
                session.getTotalChunks(),
                chunks.stream().mapToLong(UploadChunk::getSizeBytes).sum(),
                session.getStatus().name(),
                session.getCompletedFile() == null ? null : session.getCompletedFile().getId(),
                session.getExpiresAt(),
                chunks.stream()
                        .sorted(Comparator.comparingInt(UploadChunk::getChunkIndex))
                        .map(chunk -> new ChunkResponse(chunk.getChunkIndex(), chunk.getSizeBytes(), chunk.getSha256()))
                        .toList());
    }

    private long normalizeChunkSize(Long requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_CHUNK_SIZE;
        }
        return Math.max(MIN_CHUNK_SIZE, Math.min(MAX_CHUNK_SIZE, requested));
    }

    private long expectedChunkSize(UploadSession session, int chunkIndex) {
        long offset = session.getChunkSize() * chunkIndex;
        return Math.min(session.getChunkSize(), session.getSizeBytes() - offset);
    }

    private CloudFile requireFolder(User user, Long folderId) {
        CloudFile folder = fileRepository.findByIdAndOwnerIdAndDeletedFalse(folderId, user.getId())
                .orElseThrow(() -> AppException.notFound("文件夹不存在"));
        if (folder.getFileKind() != FileKind.FOLDER) {
            throw AppException.badRequest("目标不是文件夹");
        }
        return folder;
    }

    private StorageObject resolveObject(FileStorageService.StoredObject stored) {
        StorageObject object = objectRepository.findFirstBySha256AndSizeBytes(stored.sha256(), stored.size())
                .orElseGet(() -> {
                    StorageObject created = new StorageObject();
                    created.setSha256(stored.sha256());
                    created.setSizeBytes(stored.size());
                    created.setStoredName(stored.storedName());
                    created.setRelativePath(stored.relativePath());
                    created.setContentType(stored.contentType());
                    created.setExtension(stored.extension());
                    return created;
                });
        object.setRefCount(object.getRefCount() + 1);
        return objectRepository.save(object);
    }

    private String uniqueName(Long ownerId, Long parentId, String requested) {
        String baseName = requested;
        String extension = "";
        int dot = requested.lastIndexOf('.');
        if (dot > 0) {
            baseName = requested.substring(0, dot);
            extension = requested.substring(dot);
        }
        String candidate = requested;
        int index = 1;
        while (existsSibling(ownerId, parentId, candidate)) {
            candidate = baseName + " (" + index++ + ")" + extension;
        }
        return candidate;
    }

    private boolean existsSibling(Long ownerId, Long parentId, String name) {
        if (parentId == null) {
            return fileRepository.existsByOwnerIdAndParentIsNullAndNameAndDeletedFalse(ownerId, name);
        }
        return fileRepository.existsByOwnerIdAndParentIdAndNameAndDeletedFalse(ownerId, parentId, name);
    }

    private String cleanContentType(String contentType, String fileName) {
        if (contentType != null && !contentType.isBlank()) {
            return contentType.trim().toLowerCase(Locale.ROOT);
        }
        String extension = storageService.extensionOf(fileName);
        return extension.isBlank() ? "application/octet-stream" : null;
    }

    private void cleanupChunks(UploadSession session) {
        chunkRepository.deleteBySession(session);
        session.setUploadedBytes(0);
        storageService.deleteDirectory("tmp/uploads/" + session.getUploadId());
    }

    private FileStorageService.DeletedDirectory cleanupChunksWithStats(UploadSession session) {
        chunkRepository.deleteBySession(session);
        session.setUploadedBytes(0);
        return storageService.deleteDirectoryWithStats("tmp/uploads/" + session.getUploadId());
    }
}
