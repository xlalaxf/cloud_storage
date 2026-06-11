package com.cloudstorage.service;

import com.cloudstorage.dto.FileDtos.CreateFolderRequest;
import com.cloudstorage.dto.FileDtos.FileResponse;
import com.cloudstorage.dto.FileDtos.FolderTreeNode;
import com.cloudstorage.dto.FileDtos.MoveRequest;
import com.cloudstorage.dto.FileDtos.RenameRequest;
import com.cloudstorage.model.CloudFile;
import com.cloudstorage.model.FileKind;
import com.cloudstorage.model.StorageObject;
import com.cloudstorage.model.User;
import com.cloudstorage.repository.CloudFileRepository;
import com.cloudstorage.repository.StorageObjectRepository;
import com.cloudstorage.repository.UserRepository;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;
import java.sql.Timestamp;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileService {
    private static final Logger log = LoggerFactory.getLogger(FileService.class);
    private static final int ZIP_ENTRY_LIMIT = 10000;
    private static final long ZIP_EXTRACTED_SIZE_LIMIT = 3L * 1024 * 1024 * 1024;
    private static final int ZIP_EXTRACT_WORKERS = Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors()));
    private static final int DB_LOOKUP_BATCH_SIZE = 500;

    private final CloudFileRepository fileRepository;
    private final StorageObjectRepository objectRepository;
    private final UserRepository userRepository;
    private final FileStorageService storageService;
    private final FileMapper fileMapper;
    private final AuditService auditService;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public FileService(
            CloudFileRepository fileRepository,
            StorageObjectRepository objectRepository,
            UserRepository userRepository,
            FileStorageService storageService,
            FileMapper fileMapper,
            AuditService auditService,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        this.fileRepository = fileRepository;
        this.objectRepository = objectRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.fileMapper = fileMapper;
        this.auditService = auditService;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public List<FileResponse> list(User user, Long parentId) {
        if (parentId != null) {
            requireFolder(user, parentId);
            return fileRepository.findByOwnerIdAndParentIdAndDeletedFalseOrderByFileKindDescNameAsc(user.getId(), parentId)
                    .stream().map(fileMapper::toResponse).toList();
        }
        return fileRepository.findByOwnerIdAndParentIsNullAndDeletedFalseOrderByFileKindDescNameAsc(user.getId())
                .stream().map(fileMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<FolderTreeNode> folderTree(User user) {
        List<CloudFile> folders = fileRepository.findByOwnerIdAndDeletedFalseOrderByParentIdAscFileKindDescNameAsc(user.getId())
                .stream()
                .filter(file -> file.getFileKind() == FileKind.FOLDER)
                .toList();
        Map<Long, List<CloudFile>> byParent = folders.stream()
                .collect(Collectors.groupingBy(file -> file.getParent() == null ? 0L : file.getParent().getId()));
        return buildFolderTree(byParent, 0L);
    }

    @Transactional(readOnly = true)
    public List<FileResponse> listForAdmin(Long userId, Long parentId) {
        if (parentId != null) {
            return fileRepository.findByOwnerIdAndParentIdAndDeletedFalseOrderByFileKindDescNameAsc(userId, parentId)
                    .stream().map(fileMapper::toResponse).toList();
        }
        return fileRepository.findByOwnerIdAndParentIsNullAndDeletedFalseOrderByFileKindDescNameAsc(userId)
                .stream().map(fileMapper::toResponse).toList();
    }

    @Transactional
    public FileResponse createFolder(User user, CreateFolderRequest request) {
        CloudFile parent = request.parentId() == null ? null : requireFolder(user, request.parentId());
        String name = uniqueName(user.getId(), parent == null ? null : parent.getId(), storageService.sanitizeName(request.name()));
        CloudFile folder = new CloudFile();
        folder.setOwner(attachedUser(user));
        folder.setParent(parent);
        folder.setFileKind(FileKind.FOLDER);
        folder.setName(name);
        CloudFile saved = fileRepository.save(folder);
        auditService.recordFileOperation(user, saved, "CREATE_FOLDER", parentDetail(parent));
        return fileMapper.toResponse(saved);
    }

    @Transactional
    public List<FileResponse> upload(User user, Long parentId, List<MultipartFile> files) {
        CloudFile parent = parentId == null ? null : requireFolder(user, parentId);
        if (files == null || files.isEmpty()) {
            throw AppException.badRequest("请选择上传文件");
        }
        List<FileResponse> result = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String safeName = storageService.sanitizeName(file.getOriginalFilename());
            String uniqueName = uniqueName(user.getId(), parent == null ? null : parent.getId(), safeName);
            FileStorageService.StoredObject stored = storageService.store(file, user.getId(), uniqueName);
            StorageObject object = resolveObject(stored);
            CloudFile cloudFile = buildFile(attachedUser(user), parent, uniqueName, object);
            CloudFile saved = fileRepository.save(cloudFile);
            auditService.recordFileOperation(user, saved, "UPLOAD", "大小: " + object.getSizeBytes() + " bytes; " + parentDetail(parent));
            result.add(fileMapper.toResponse(saved));
        }
        return result;
    }

    @Transactional
    public FileResponse rename(User user, Long fileId, RenameRequest request) {
        CloudFile file = requireOwned(user, fileId);
        String oldName = file.getName();
        String safeName = storageService.sanitizeName(request.name());
        Long parentId = file.getParent() == null ? null : file.getParent().getId();
        if (!file.getName().equals(safeName)) {
            file.setName(uniqueName(user.getId(), parentId, safeName));
            auditService.recordFileOperation(user, file, "RENAME", oldName + " -> " + file.getName());
        }
        return fileMapper.toResponse(file);
    }

    @Transactional
    public FileResponse move(User user, Long fileId, MoveRequest request) {
        CloudFile file = requireOwned(user, fileId);
        CloudFile moved = moveFile(user, file, request.targetParentId());
        return fileMapper.toResponse(moved);
    }

    @Transactional
    public List<FileResponse> moveBatch(User user, List<Long> fileIds, Long targetParentId) {
        if (fileIds == null || fileIds.isEmpty()) {
            throw AppException.badRequest("请选择文件");
        }
        return fileIds.stream()
                .map(fileId -> fileMapper.toResponse(moveFile(user, requireOwned(user, fileId), targetParentId)))
                .toList();
    }

    @Transactional
    public FileResponse copy(User user, Long fileId, MoveRequest request) {
        CloudFile file = requireOwned(user, fileId);
        CloudFile targetParent = request.targetParentId() == null ? null : requireFolder(user, request.targetParentId());
        CloudFile copied = copyRecursive(file, targetParent, attachedUser(user));
        auditService.recordFileOperation(user, copied, "COPY", "来源: " + file.getName() + "; " + parentDetail(targetParent));
        return fileMapper.toResponse(copied);
    }

    @Transactional
    public List<FileResponse> copyBatch(User user, List<Long> fileIds, Long targetParentId) {
        if (fileIds == null || fileIds.isEmpty()) {
            throw AppException.badRequest("请选择文件");
        }
        CloudFile targetParent = targetParentId == null ? null : requireFolder(user, targetParentId);
        User owner = attachedUser(user);
        List<FileResponse> responses = new ArrayList<>();
        for (Long fileId : fileIds) {
            CloudFile file = requireOwned(user, fileId);
            CloudFile copied = copyRecursive(file, targetParent, owner);
            auditService.recordFileOperation(user, copied, "COPY", "来源: " + file.getName() + "; " + parentDetail(targetParent));
            responses.add(fileMapper.toResponse(copied));
        }
        return responses;
    }

    @Transactional
    public void delete(User user, Long fileId) {
        CloudFile file = requireOwned(user, fileId);
        auditService.recordFileOperation(user, file, "DELETE", parentDetail(file.getParent()));
        deleteRecursive(file);
    }

    @Transactional
    public void deleteBatch(User user, List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            throw AppException.badRequest("请选择文件");
        }
        for (Long fileId : fileIds) {
            CloudFile file = requireOwned(user, fileId);
            auditService.recordFileOperation(user, file, "DELETE", parentDetail(file.getParent()));
            deleteRecursive(file);
        }
    }

    @Transactional
    public void deleteAsAdmin(User admin, Long fileId) {
        CloudFile file = fileRepository.findById(fileId).orElseThrow(() -> AppException.notFound("文件不存在"));
        auditService.recordFileOperation(admin, file, "ADMIN_DELETE", "管理员删除用户文件: " + file.getOwner().getUsername());
        deleteRecursive(file);
    }

    @Transactional
    public DownloadPayload downloadOwned(User user, Long fileId, boolean increaseCount) {
        CloudFile file = requireOwned(user, fileId);
        DownloadPayload payload = download(file, increaseCount);
        auditService.recordFileOperation(user, file, increaseCount ? "DOWNLOAD" : "PREVIEW", null);
        return payload;
    }

    @Transactional
    public DownloadPayload download(CloudFile file, boolean increaseCount) {
        if (file.isDeleted() || file.getFileKind() != FileKind.FILE) {
            throw AppException.notFound("文件不存在");
        }
        if (increaseCount) {
            file.setDownloadCount(file.getDownloadCount() + 1);
        }
        StorageObject object = requireObject(file);
        Resource resource = storageService.resource(object.getRelativePath());
        return new DownloadPayload(resource, file.getName(), mediaType(object.getContentType()), object.getSizeBytes());
    }

    @Transactional
    public DownloadPayload downloadFolder(CloudFile folder, boolean increaseCount) {
        if (folder.isDeleted() || folder.getFileKind() != FileKind.FOLDER) {
            throw AppException.notFound("文件夹不存在");
        }
        if (increaseCount) {
            folder.setDownloadCount(folder.getDownloadCount() + 1);
        }
        Path zipPath = storageService.createTempFile("folder-", ".zip");
        try (OutputStream outputStream = Files.newOutputStream(zipPath);
                ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            writeFolderToZip(folder, zipPart(folder.getName()) + "/", zipOutputStream);
        } catch (Exception ex) {
            deleteTempZip(zipPath);
            if (ex instanceof AppException appException) {
                throw appException;
            }
            throw AppException.badRequest("文件夹打包失败");
        }
        try {
            return new DownloadPayload(
                    tempZipResource(zipPath),
                    zipFilename(folder.getName()),
                    MediaType.parseMediaType("application/zip"),
                    Files.size(zipPath));
        } catch (IOException ex) {
            deleteTempZip(zipPath);
            throw AppException.badRequest("文件夹打包失败");
        }
    }

    public List<FileResponse> extractZip(User user, Long fileId) {
        return extractZip(user.getId(), fileId, null);
    }

    @Transactional(readOnly = true)
    public void validateExtractable(User user, Long fileId) {
        CloudFile archive = requireOwned(user, fileId);
        if (archive.getFileKind() != FileKind.FILE || !isZip(archive)) {
            throw AppException.badRequest("当前仅支持 ZIP 文件在线解压");
        }
    }

    @Transactional(readOnly = true)
    public String extractableFileName(User user, Long fileId) {
        CloudFile archive = requireOwned(user, fileId);
        if (archive.getFileKind() != FileKind.FILE || !isZip(archive)) {
            throw AppException.badRequest("当前仅支持 ZIP 文件在线解压");
        }
        return archive.getName();
    }

    public void extractZipJob(ExtractJobService.ExtractJob job) {
        List<FileResponse> extracted = extractZip(job.userId(), job.fileId(), job);
        if (!extracted.isEmpty()) {
            job.complete(extracted.get(0));
        }
    }

    private List<FileResponse> extractZip(Long userId, Long fileId, ExtractJobService.ExtractJob job) {
        long totalStartedNanos = System.nanoTime();
        ExtractArchive archive = loadExtractArchive(userId, fileId);
        if (job != null) {
            job.scanning(archive.name());
        }
        long scanStartedNanos = System.nanoTime();
        List<ZipEntryPlan> entries = readZipEntries(archive.archivePath());
        ZipStats stats = scanZipEntries(entries);
        log.info(
                "Extract scan done fileId={} fileName={} entries={} files={} totalBytes={} elapsedMs={}",
                fileId,
                archive.name(),
                stats.totalEntries(),
                stats.totalFiles(),
                stats.totalBytes(),
                elapsedMs(scanStartedNanos));
        List<FileStorageService.StoredObject> storedObjects = new ArrayList<>();
        ExtractPlan plan = null;
        try {
            if (job != null) {
                job.extracting(stats.totalEntries(), stats.totalBytes());
            }
            long planStartedNanos = System.nanoTime();
            plan = transactionTemplate.execute(status -> createExtractPlan(userId, archive, entries, job));
            if (plan == null) {
                throw AppException.badRequest("解压失败，请稍后重试");
            }
            log.info(
                    "Extract plan done fileId={} rootId={} plannedFiles={} elapsedMs={}",
                    fileId,
                    plan.rootId(),
                    plan.files().size(),
                    elapsedMs(planStartedNanos));
            long extractStartedNanos = System.nanoTime();
            List<ExtractedObject> extractedObjects = extractPlannedFiles(
                    archive.archivePath(), userId, plan.files(), stats, job, storedObjects);
            log.info(
                    "Extract streams done fileId={} extractedFiles={} storedObjects={} elapsedMs={}",
                    fileId,
                    extractedObjects.size(),
                    storedObjects.size(),
                    elapsedMs(extractStartedNanos));
            if (job != null) {
                job.saving();
            }
            long persistStartedNanos = System.nanoTime();
            ExtractPlan completedPlan = plan;
            List<FileResponse> result = transactionTemplate.execute(status ->
                    persistExtractedFiles(userId, archive, completedPlan, extractedObjects));
            log.info(
                    "Extract persist done fileId={} elapsedMs={} totalElapsedMs={}",
                    fileId,
                    elapsedMs(persistStartedNanos),
                    elapsedMs(totalStartedNanos));
            return result;
        } catch (Exception ex) {
            if (ex instanceof AppException appException) {
                cleanupCreatedObjects(storedObjects);
                cleanupExtractRoot(userId, plan);
                throw appException;
            }
            cleanupCreatedObjects(storedObjects);
            cleanupExtractRoot(userId, plan);
            throw AppException.badRequest("解压失败，请确认压缩包未损坏");
        }
    }

    private ExtractArchive loadExtractArchive(Long userId, Long fileId) {
        return transactionTemplate.execute(status -> {
            User user = userRepository.findById(userId).orElseThrow(() -> AppException.notFound("用户不存在"));
            CloudFile archive = requireOwned(user, fileId);
            if (archive.getFileKind() != FileKind.FILE || !isZip(archive)) {
                throw AppException.badRequest("当前仅支持 ZIP 文件在线解压");
            }
            Path archivePath = storageService.objectPath(requireObject(archive).getRelativePath());
            return new ExtractArchive(archive.getId(), archive.getName(), archivePath);
        });
    }

    @Transactional(readOnly = true)
    public CloudFile requireOwned(User user, Long fileId) {
        return fileRepository.findByIdAndOwnerIdAndDeletedFalse(fileId, user.getId())
                .orElseThrow(() -> AppException.notFound("文件不存在"));
    }

    @Transactional(readOnly = true)
    public CloudFile requireExisting(Long fileId) {
        CloudFile file = fileRepository.findById(fileId).orElseThrow(() -> AppException.notFound("文件不存在"));
        if (file.isDeleted()) {
            throw AppException.notFound("文件不存在");
        }
        return file;
    }

    @Transactional(readOnly = true)
    public boolean isSameOrDescendant(CloudFile candidate, CloudFile root) {
        CloudFile cursor = candidate;
        while (cursor != null) {
            if (Objects.equals(cursor.getId(), root.getId())) {
                return true;
            }
            cursor = cursor.getParent();
        }
        return false;
    }

    public FileResponse toResponse(CloudFile file) {
        return fileMapper.toResponse(file);
    }

    private CloudFile buildFile(User owner, CloudFile parent, String name, StorageObject object) {
        CloudFile cloudFile = new CloudFile();
        cloudFile.setOwner(owner);
        cloudFile.setParent(parent);
        cloudFile.setFileKind(FileKind.FILE);
        cloudFile.setName(name);
        cloudFile.setObject(object);
        cloudFile.setStoredName(object.getStoredName());
        cloudFile.setRelativePath(object.getRelativePath());
        cloudFile.setContentType(object.getContentType());
        cloudFile.setExtension(object.getExtension());
        cloudFile.setSizeBytes(object.getSizeBytes());
        return cloudFile;
    }

    private List<FolderTreeNode> buildFolderTree(Map<Long, List<CloudFile>> byParent, Long parentId) {
        return byParent.getOrDefault(parentId, List.of())
                .stream()
                .sorted(Comparator.comparing(CloudFile::getName, String.CASE_INSENSITIVE_ORDER))
                .map(folder -> new FolderTreeNode(
                        folder.getId(),
                        folder.getParent() == null ? null : folder.getParent().getId(),
                        folder.getName(),
                        buildFolderTree(byParent, folder.getId())))
                .toList();
    }

    private CloudFile moveFile(User user, CloudFile file, Long targetParentId) {
        CloudFile targetParent = targetParentId == null ? null : requireFolder(user, targetParentId);
        if (targetParent != null && isSelfOrDescendant(targetParent, file)) {
            throw AppException.badRequest("不能移动到自身或子文件夹下");
        }
        String oldParent = parentDetail(file.getParent());
        Long targetId = targetParent == null ? null : targetParent.getId();
        String targetName = uniqueName(user.getId(), targetId, file.getName(), file.getId());
        file.setParent(targetParent);
        file.setName(targetName);
        auditService.recordFileOperation(user, file, "MOVE", oldParent + " -> " + parentDetail(targetParent));
        return file;
    }

    private CloudFile copyRecursive(CloudFile source, CloudFile targetParent, User owner) {
        Long targetParentId = targetParent == null ? null : targetParent.getId();
        CloudFile copy = new CloudFile();
        copy.setOwner(owner);
        copy.setParent(targetParent);
        copy.setFileKind(source.getFileKind());
        copy.setName(uniqueName(owner.getId(), targetParentId, source.getName()));
        if (source.getFileKind() == FileKind.FILE) {
            StorageObject object = requireObject(source);
            retainObject(object);
            copy.setObject(object);
            copy.setStoredName(object.getStoredName());
            copy.setRelativePath(object.getRelativePath());
            copy.setContentType(object.getContentType());
            copy.setExtension(object.getExtension());
            copy.setSizeBytes(object.getSizeBytes());
        }
        CloudFile saved = fileRepository.save(copy);
        if (source.getFileKind() == FileKind.FOLDER) {
            List<CloudFile> children = fileRepository.findByParentIdAndDeletedFalseOrderByFileKindDescNameAsc(source.getId());
            for (CloudFile child : children) {
                copyRecursive(child, saved, owner);
            }
        }
        return saved;
    }

    private void deleteRecursive(CloudFile file) {
        if (file.isDeleted()) {
            return;
        }
        file.setDeleted(true);
        if (file.getFileKind() == FileKind.FOLDER) {
            List<CloudFile> children = fileRepository.findByParentIdAndDeletedFalseOrderByFileKindDescNameAsc(file.getId());
            children.forEach(this::deleteRecursive);
        } else {
            StorageObject object = file.getObject();
            file.setObject(null);
            releaseObject(object);
        }
    }

    private StorageObject resolveObject(FileStorageService.StoredObject stored) {
        StorageObject object = objectRepository.findFirstBySha256AndSizeBytes(stored.sha256(), stored.size())
                .orElseGet(() -> {
                    return newStorageObject(stored);
                });
        retainObject(object);
        return objectRepository.save(object);
    }

    private StorageObject newStorageObject(FileStorageService.StoredObject stored) {
        StorageObject created = new StorageObject();
        created.setSha256(stored.sha256());
        created.setSizeBytes(stored.size());
        created.setStoredName(stored.storedName());
        created.setRelativePath(stored.relativePath());
        created.setContentType(stored.contentType());
        created.setExtension(stored.extension());
        return created;
    }

    private StorageObject requireObject(CloudFile file) {
        StorageObject object = file.getObject();
        if (object != null) {
            return object;
        }
        if (file.getRelativePath() == null || file.getRelativePath().isBlank()) {
            throw AppException.notFound("文件不存在");
        }
        StorageObject legacy = new StorageObject();
        legacy.setSha256("legacy-" + file.getId());
        legacy.setSizeBytes(file.getSizeBytes());
        legacy.setStoredName(file.getStoredName());
        legacy.setRelativePath(file.getRelativePath());
        legacy.setContentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType());
        legacy.setExtension(file.getExtension());
        legacy.setRefCount(1);
        file.setObject(objectRepository.save(legacy));
        return file.getObject();
    }

    private void retainObject(StorageObject object) {
        object.setRefCount(object.getRefCount() + 1);
    }

    private void releaseObject(StorageObject object) {
        if (object == null) {
            return;
        }
        long nextRefCount = Math.max(0, object.getRefCount() - 1);
        object.setRefCount(nextRefCount);
        if (nextRefCount == 0) {
            fileRepository.detachObject(object.getId());
            fileRepository.flush();
            storageService.deleteObject(object.getRelativePath());
            objectRepository.delete(object);
        }
    }

    private CloudFile requireFolder(User user, Long folderId) {
        CloudFile folder = requireOwned(user, folderId);
        if (folder.getFileKind() != FileKind.FOLDER) {
            throw AppException.badRequest("目标不是文件夹");
        }
        return folder;
    }

    private boolean isSelfOrDescendant(CloudFile targetParent, CloudFile source) {
        CloudFile cursor = targetParent;
        while (cursor != null) {
            if (Objects.equals(cursor.getId(), source.getId())) {
                return true;
            }
            cursor = cursor.getParent();
        }
        return false;
    }

    private String uniqueName(Long ownerId, Long parentId, String requested) {
        return uniqueName(ownerId, parentId, requested, null);
    }

    private String uniqueName(Long ownerId, Long parentId, String requested, Long excludeId) {
        String baseName = requested;
        String extension = "";
        int dot = requested.lastIndexOf('.');
        if (dot > 0) {
            baseName = requested.substring(0, dot);
            extension = requested.substring(dot);
        }
        String candidate = requested;
        int index = 1;
        while (existsSibling(ownerId, parentId, candidate, excludeId)) {
            candidate = baseName + " (" + index++ + ")" + extension;
        }
        return candidate;
    }

    private boolean existsSibling(Long ownerId, Long parentId, String name) {
        return existsSibling(ownerId, parentId, name, null);
    }

    private boolean existsSibling(Long ownerId, Long parentId, String name, Long excludeId) {
        if (parentId == null) {
            if (excludeId != null) {
                return fileRepository.existsByOwnerIdAndParentIsNullAndNameAndDeletedFalseAndIdNot(ownerId, name, excludeId);
            }
            return fileRepository.existsByOwnerIdAndParentIsNullAndNameAndDeletedFalse(ownerId, name);
        }
        if (excludeId != null) {
            return fileRepository.existsByOwnerIdAndParentIdAndNameAndDeletedFalseAndIdNot(ownerId, parentId, name, excludeId);
        }
        return fileRepository.existsByOwnerIdAndParentIdAndNameAndDeletedFalse(ownerId, parentId, name);
    }

    private String nameKey(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    private String parentDetail(CloudFile parent) {
        return parent == null ? "父目录: 根目录" : "父目录: " + parent.getName() + " (#" + parent.getId() + ")";
    }

    private CloudFile createExtractRoot(User user, CloudFile archive) {
        CloudFile parent = archive.getParent();
        String baseName = archive.getName();
        int dot = baseName.lastIndexOf('.');
        if (dot > 0) {
            baseName = baseName.substring(0, dot);
        }
        String folderName = uniqueName(user.getId(), parent == null ? null : parent.getId(), baseName + "_unzipped");
        CloudFile folder = new CloudFile();
        folder.setOwner(attachedUser(user));
        folder.setParent(parent);
        folder.setFileKind(FileKind.FOLDER);
        folder.setName(folderName);
        return fileRepository.save(folder);
    }

    private ExtractPlan createExtractPlan(
            Long userId,
            ExtractArchive archive,
            List<ZipEntryPlan> entries,
            ExtractJobService.ExtractJob job) {
        User user = userRepository.findById(userId).orElseThrow(() -> AppException.notFound("用户不存在"));
        CloudFile archiveFile = requireOwned(user, archive.fileId());
        CloudFile extractRoot = createExtractRoot(user, archiveFile);
        ExtractContext context = new ExtractContext(user, extractRoot);
        List<PlannedExtractFile> files = new ArrayList<>();
        int processedEntries = 0;
        for (ZipEntryPlan entry : entries) {
            List<String> parts = entry.parts();
            if (!parts.isEmpty()) {
                CloudFile parent = ensurePlannedZipParent(context, parts.subList(0, parts.size() - 1));
                String name = storageService.sanitizeName(parts.get(parts.size() - 1));
                if (entry.directory()) {
                    context.ensureFolder(parent, name);
                } else {
                    String uniqueName = context.uniqueName(parent, name);
                    String contentType = URLConnection.guessContentTypeFromName(uniqueName);
                    files.add(new PlannedExtractFile(
                            files.size(),
                            entry.name(),
                            entry.size(),
                            parent.getId(),
                            uniqueName,
                            contentType));
                }
            }
            processedEntries++;
            if (job != null) {
                job.advance(processedEntries, 0, entry.name());
            }
        }
        return new ExtractPlan(extractRoot.getId(), files);
    }

    private CloudFile ensurePlannedZipParent(ExtractContext context, List<String> folderParts) {
        CloudFile parent = context.root();
        for (String rawPart : folderParts) {
            String part = storageService.sanitizeName(rawPart);
            parent = context.ensureFolder(parent, part);
        }
        return parent;
    }

    private List<ExtractedObject> extractPlannedFiles(
            Path archivePath,
            Long userId,
            List<PlannedExtractFile> files,
            ZipStats stats,
            ExtractJobService.ExtractJob job,
            Collection<FileStorageService.StoredObject> storedObjects) {
        if (files.isEmpty()) {
            return List.of();
        }
        int workerCount = Math.min(ZIP_EXTRACT_WORKERS, files.size());
        ExecutorService executor = Executors.newFixedThreadPool(workerCount, namedThreadFactory("zip-extract-"));
        ExecutorCompletionService<List<ExtractedObject>> completionService = new ExecutorCompletionService<>(executor);
        ConcurrentLinkedQueue<PlannedExtractFile> queue = new ConcurrentLinkedQueue<>(files);
        AtomicLong processedBytes = new AtomicLong(0);
        AtomicLong processedFileEntries = new AtomicLong(0);
        AtomicReference<String> currentEntryName = new AtomicReference<>("");
        AtomicReference<AppException> progressFailure = new AtomicReference<>();
        Thread progressThread = null;
        List<Future<List<ExtractedObject>>> futures = new ArrayList<>();
        try {
            if (job != null) {
                progressThread = startExtractProgressReporter(
                        job,
                        stats.totalEntries(),
                        files.size(),
                        processedFileEntries,
                        processedBytes,
                        currentEntryName,
                        progressFailure);
            }
            for (int i = 0; i < workerCount; i++) {
                futures.add(completionService.submit(() -> extractZipWorker(
                        archivePath,
                        userId,
                        queue,
                        processedBytes,
                        processedFileEntries,
                        currentEntryName)));
            }
            List<ExtractedObject> extracted = new ArrayList<>();
            for (int i = 0; i < workerCount; i++) {
                List<ExtractedObject> results = completionService.take().get();
                for (ExtractedObject result : results) {
                    storedObjects.add(result.storedObject());
                    extracted.add(result);
                }
                AppException failure = progressFailure.get();
                if (failure != null) {
                    throw failure;
                }
            }
            extracted.sort(Comparator.comparingInt(ExtractedObject::index));
            if (job != null) {
                job.advance(stats.totalEntries(), processedBytes.get(), "");
            }
            return extracted;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw AppException.badRequest("解压任务已中断");
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof AppException appException) {
                throw appException;
            }
            throw AppException.badRequest("解压失败，请确认压缩包未损坏");
        } finally {
            for (Future<List<ExtractedObject>> future : futures) {
                future.cancel(true);
            }
            executor.shutdownNow();
            if (progressThread != null) {
                progressThread.interrupt();
            }
        }
    }

    private Thread startExtractProgressReporter(
            ExtractJobService.ExtractJob job,
            int totalEntries,
            int totalFiles,
            AtomicLong processedFileEntries,
            AtomicLong processedBytes,
            AtomicReference<String> currentEntryName,
            AtomicReference<AppException> failure) {
        Thread thread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted() && processedFileEntries.get() < totalFiles) {
                    job.advance(estimateProcessedEntries(totalEntries, totalFiles, processedFileEntries.get()),
                            processedBytes.get(),
                            currentEntryName.get());
                    Thread.sleep(500);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException ex) {
                failure.compareAndSet(null, AppException.badRequest("解压进度更新失败"));
            }
        }, "zip-extract-progress-" + System.nanoTime());
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private int estimateProcessedEntries(int totalEntries, int totalFiles, long processedFiles) {
        if (totalFiles <= 0) {
            return totalEntries;
        }
        return Math.min(totalEntries, (int) Math.floor((processedFiles * totalEntries) / (double) totalFiles));
    }

    private List<ExtractedObject> extractZipWorker(
            Path archivePath,
            Long userId,
            ConcurrentLinkedQueue<PlannedExtractFile> queue,
            AtomicLong processedBytes,
            AtomicLong processedFileEntries,
            AtomicReference<String> currentEntryName) {
        try (ZipFile zipFile = openZipFile(archivePath)) {
            List<ExtractedObject> extracted = new ArrayList<>();
            PlannedExtractFile file;
            while ((file = queue.poll()) != null) {
                currentEntryName.set(file.entryName());
                ZipEntry entry = zipFile.getEntry(file.entryName());
                if (entry == null || entry.isDirectory()) {
                    throw AppException.badRequest("压缩包中存在无法读取的文件");
                }
                try (InputStream entryStream = zipFile.getInputStream(entry)) {
                    FileStorageService.StoredObject stored = storageService.storeStreamFast(
                            entryStream,
                            userId,
                            file.name(),
                            file.contentType(),
                            processedBytes::addAndGet);
                    processedFileEntries.incrementAndGet();
                    extracted.add(new ExtractedObject(file.index(), file.parentId(), file.name(), stored));
                }
            }
            return extracted;
        } catch (IllegalArgumentException ex) {
            throw AppException.badRequest("压缩包中存在非法路径");
        } catch (IOException ex) {
            throw AppException.badRequest("解压失败，请确认压缩包未损坏");
        }
    }

    private List<FileResponse> persistExtractedFiles(
            Long userId,
            ExtractArchive archive,
            ExtractPlan plan,
            List<ExtractedObject> extractedObjects) {
        User user = userRepository.findById(userId).orElseThrow(() -> AppException.notFound("用户不存在"));
        CloudFile extractRoot = fileRepository.findByIdAndOwnerIdAndDeletedFalse(plan.rootId(), userId)
                .orElseThrow(() -> AppException.notFound("解压目录不存在"));
        long parentStartedNanos = System.nanoTime();
        Map<Long, CloudFile> parentCache = loadExtractParents(userId, extractRoot, extractedObjects);
        log.info(
                "Extract parents loaded rootId={} parents={} elapsedMs={}",
                plan.rootId(),
                parentCache.size(),
                elapsedMs(parentStartedNanos));

        long objectStartedNanos = System.nanoTime();
        Map<StoredObjectKey, StorageObject> objectByKey = resolveExtractedObjects(extractedObjects);
        log.info(
                "Extract objects resolved rootId={} uniqueObjects={} elapsedMs={}",
                plan.rootId(),
                objectByKey.size(),
                elapsedMs(objectStartedNanos));

        long fileStartedNanos = System.nanoTime();
        List<ExtractedFileInsert> filesToInsert = new ArrayList<>(extractedObjects.size());
        for (ExtractedObject extractedObject : extractedObjects) {
            StorageObject object = objectByKey.get(storedObjectKey(extractedObject.storedObject()));
            CloudFile parent = parentCache.get(extractedObject.parentId());
            if (object == null || parent == null) {
                throw AppException.notFound("解压目录不存在");
            }
            filesToInsert.add(new ExtractedFileInsert(extractedObject.name(), parent.getId(), object));
        }
        batchInsertExtractedFiles(user.getId(), filesToInsert);
        log.info(
                "Extract files saved rootId={} files={} elapsedMs={}",
                plan.rootId(),
                filesToInsert.size(),
                elapsedMs(fileStartedNanos));

        List<FileResponse> extracted = new ArrayList<>(1);
        extracted.add(fileMapper.toResponse(extractRoot));
        auditService.recordFileOperation(user, extractRoot, "EXTRACT", "来源压缩包: " + archive.name());
        return extracted;
    }

    private void batchInsertExtractedFiles(Long ownerId, List<ExtractedFileInsert> files) {
        if (files.isEmpty()) {
            return;
        }
        for (List<ExtractedFileInsert> batch : batches(files, DB_LOOKUP_BATCH_SIZE)) {
            jdbcTemplate.batchUpdate(
                    """
                            insert into files
                            (owner_id, parent_id, object_id, file_kind, name, stored_name, relative_path, content_type,
                             extension, size_bytes, download_count, deleted, created_at, updated_at)
                            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    batch,
                    batch.size(),
                    (statement, file) -> {
                        StorageObject object = file.object();
                        Timestamp now = Timestamp.from(Instant.now());
                        statement.setLong(1, ownerId);
                        statement.setLong(2, file.parentId());
                        statement.setLong(3, object.getId());
                        statement.setString(4, FileKind.FILE.name());
                        statement.setString(5, file.name());
                        statement.setString(6, object.getStoredName());
                        statement.setString(7, object.getRelativePath());
                        statement.setString(8, object.getContentType());
                        statement.setString(9, object.getExtension());
                        statement.setLong(10, object.getSizeBytes());
                        statement.setLong(11, 0);
                        statement.setBoolean(12, false);
                        statement.setTimestamp(13, now);
                        statement.setTimestamp(14, now);
                    });
        }
    }

    private Map<Long, CloudFile> loadExtractParents(
            Long userId,
            CloudFile extractRoot,
            List<ExtractedObject> extractedObjects) {
        Map<Long, CloudFile> parentCache = new HashMap<>();
        parentCache.put(extractRoot.getId(), extractRoot);
        Set<Long> parentIds = new HashSet<>();
        for (ExtractedObject extractedObject : extractedObjects) {
            if (extractedObject.parentId() != null) {
                parentIds.add(extractedObject.parentId());
            }
        }
        parentIds.remove(extractRoot.getId());
        for (List<Long> batch : batches(parentIds, DB_LOOKUP_BATCH_SIZE)) {
            fileRepository.findByIdInAndOwnerIdAndDeletedFalse(batch, userId)
                    .forEach(parent -> parentCache.put(parent.getId(), parent));
        }
        for (Long parentId : parentIds) {
            if (!parentCache.containsKey(parentId)) {
                throw AppException.notFound("解压目录不存在");
            }
        }
        return parentCache;
    }

    private Map<StoredObjectKey, StorageObject> resolveExtractedObjects(List<ExtractedObject> extractedObjects) {
        Map<StoredObjectKey, FileStorageService.StoredObject> storedByKey = new HashMap<>();
        Map<StoredObjectKey, Integer> refCountsByKey = new HashMap<>();
        Set<String> sha256s = new HashSet<>();
        for (ExtractedObject extractedObject : extractedObjects) {
            FileStorageService.StoredObject stored = extractedObject.storedObject();
            StoredObjectKey key = storedObjectKey(stored);
            storedByKey.putIfAbsent(key, stored);
            refCountsByKey.merge(key, 1, Integer::sum);
            sha256s.add(stored.sha256());
        }

        Map<StoredObjectKey, StorageObject> objectByKey = new HashMap<>();
        for (List<String> batch : batches(sha256s, DB_LOOKUP_BATCH_SIZE)) {
            for (StorageObject object : objectRepository.findBySha256In(batch)) {
                StoredObjectKey key = storedObjectKey(object);
                if (storedByKey.containsKey(key)) {
                    objectByKey.putIfAbsent(key, object);
                }
            }
        }

        List<StorageObject> existingObjects = new ArrayList<>(objectByKey.values());
        batchRetainStorageObjects(existingObjects, refCountsByKey);

        List<FileStorageService.StoredObject> missingObjects = new ArrayList<>();
        for (Map.Entry<StoredObjectKey, FileStorageService.StoredObject> entry : storedByKey.entrySet()) {
            StoredObjectKey key = entry.getKey();
            if (!objectByKey.containsKey(key)) {
                missingObjects.add(entry.getValue());
            }
        }
        batchInsertStorageObjects(missingObjects, refCountsByKey);

        if (!missingObjects.isEmpty()) {
            objectByKey.clear();
            for (List<String> batch : batches(sha256s, DB_LOOKUP_BATCH_SIZE)) {
                for (StorageObject object : objectRepository.findBySha256In(batch)) {
                    StoredObjectKey key = storedObjectKey(object);
                    if (storedByKey.containsKey(key)) {
                        objectByKey.putIfAbsent(key, object);
                    }
                }
            }
        }
        if (objectByKey.size() != storedByKey.size()) {
            throw AppException.badRequest("解压文件记录保存失败");
        }
        return objectByKey;
    }

    private void batchRetainStorageObjects(
            List<StorageObject> objects,
            Map<StoredObjectKey, Integer> refCountsByKey) {
        if (objects.isEmpty()) {
            return;
        }
        for (List<StorageObject> batch : batches(objects, DB_LOOKUP_BATCH_SIZE)) {
            jdbcTemplate.batchUpdate(
                    "update storage_objects set ref_count = ref_count + ?, updated_at = ? where id = ?",
                    batch,
                    batch.size(),
                    (statement, object) -> {
                        statement.setLong(1, refCountsByKey.getOrDefault(storedObjectKey(object), 0));
                        statement.setTimestamp(2, Timestamp.from(Instant.now()));
                        statement.setLong(3, object.getId());
                    });
        }
    }

    private void batchInsertStorageObjects(
            List<FileStorageService.StoredObject> objects,
            Map<StoredObjectKey, Integer> refCountsByKey) {
        if (objects.isEmpty()) {
            return;
        }
        for (List<FileStorageService.StoredObject> batch : batches(objects, DB_LOOKUP_BATCH_SIZE)) {
            jdbcTemplate.batchUpdate(
                    """
                            insert into storage_objects
                            (sha256, size_bytes, stored_name, relative_path, content_type, extension, ref_count, created_at, updated_at)
                            values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    batch,
                    batch.size(),
                    (statement, stored) -> {
                        Timestamp now = Timestamp.from(Instant.now());
                        statement.setString(1, stored.sha256());
                        statement.setLong(2, stored.size());
                        statement.setString(3, stored.storedName());
                        statement.setString(4, stored.relativePath());
                        statement.setString(5, stored.contentType());
                        statement.setString(6, stored.extension());
                        statement.setLong(7, refCountsByKey.getOrDefault(storedObjectKey(stored), 0));
                        statement.setTimestamp(8, now);
                        statement.setTimestamp(9, now);
                    });
        }
    }

    private void cleanupCreatedObjects(List<FileStorageService.StoredObject> storedObjects) {
        for (FileStorageService.StoredObject object : storedObjects) {
            if (object.created()) {
                try {
                    storageService.deleteObject(object.relativePath());
                } catch (AppException ignored) {
                    // Cleanup is best effort; the original extraction error should stay visible.
                }
            }
        }
    }

    private void cleanupExtractRoot(Long userId, ExtractPlan plan) {
        if (plan == null || plan.rootId() == null) {
            return;
        }
        try {
            transactionTemplate.executeWithoutResult(status ->
                    fileRepository.findByIdAndOwnerIdAndDeletedFalse(plan.rootId(), userId)
                            .ifPresent(this::deleteRecursive));
        } catch (RuntimeException ignored) {
            // Best effort cleanup for a failed extraction tree.
        }
    }

    private List<ZipEntryPlan> readZipEntries(Path archivePath) {
        try (ZipFile zipFile = openZipFile(archivePath)) {
            List<ZipEntryPlan> entries = new ArrayList<>();
            Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                entries.add(new ZipEntryPlan(
                        entry.getName(),
                        entry.isDirectory(),
                        entry.getSize(),
                        safeZipParts(entry.getName())));
            }
            return entries;
        } catch (IllegalArgumentException ex) {
            throw AppException.badRequest("压缩包中存在非法路径");
        } catch (IOException ex) {
            throw AppException.badRequest("解压失败，请确认压缩包未损坏");
        }
    }

    private ZipFile openZipFile(Path archivePath) throws IOException {
        try {
            return new ZipFile(archivePath.toFile(), StandardCharsets.UTF_8);
        } catch (ZipException utf8Exception) {
            return new ZipFile(archivePath.toFile(), Charset.forName("GBK"));
        }
    }

    private ZipStats scanZipEntries(List<ZipEntryPlan> entries) {
        if (entries.size() > ZIP_ENTRY_LIMIT) {
            throw AppException.badRequest("压缩包文件数量超过限制");
        }
        long totalBytes = 0;
        int totalFiles = 0;
        for (ZipEntryPlan entry : entries) {
            long size = entry.size();
            if (!entry.directory()) {
                totalFiles++;
                if (size > 0) {
                    totalBytes += size;
                    if (totalBytes > ZIP_EXTRACTED_SIZE_LIMIT) {
                        throw AppException.badRequest("压缩包解压后体积超过限制");
                    }
                }
            }
        }
        return new ZipStats(entries.size(), totalFiles, totalBytes);
    }

    private final class ExtractContext {
        private final User user;
        private final CloudFile root;
        private final Map<Long, Map<String, CloudFile>> foldersByParent = new HashMap<>();
        private final Map<Long, Set<String>> namesByParent = new HashMap<>();

        private ExtractContext(User user, CloudFile root) {
            this.user = user;
            this.root = root;
            registerName(root.getParent() == null ? null : root.getParent().getId(), root.getName());
        }

        private CloudFile root() {
            return root;
        }

        private CloudFile ensureFolder(CloudFile parent, String requestedName) {
            Long parentId = parent.getId();
            String cacheKey = nameKey(requestedName);
            Map<String, CloudFile> childFolders = foldersByParent.computeIfAbsent(parentId, ignored -> new HashMap<>());
            CloudFile cached = childFolders.get(cacheKey);
            if (cached != null) {
                return cached;
            }
            String name = uniqueName(parent, requestedName);
            CloudFile folder = new CloudFile();
            folder.setOwner(attachedUser(user));
            folder.setParent(parent);
            folder.setFileKind(FileKind.FOLDER);
            folder.setName(name);
            CloudFile saved = fileRepository.save(folder);
            childFolders.put(cacheKey, saved);
            childFolders.put(nameKey(name), saved);
            registerName(parentId, name);
            return saved;
        }

        private String uniqueName(CloudFile parent, String requestedName) {
            Long parentId = parent.getId();
            Set<String> names = namesByParent.computeIfAbsent(parentId, ignored -> new HashSet<>());
            String baseName = requestedName;
            String extension = "";
            int dot = requestedName.lastIndexOf('.');
            if (dot > 0) {
                baseName = requestedName.substring(0, dot);
                extension = requestedName.substring(dot);
            }
            String candidate = requestedName;
            int index = 1;
            while (!names.add(nameKey(candidate))) {
                candidate = baseName + " (" + index++ + ")" + extension;
            }
            return candidate;
        }

        private void registerName(Long parentId, String name) {
            if (parentId != null && name != null) {
                namesByParent.computeIfAbsent(parentId, ignored -> new HashSet<>()).add(nameKey(name));
            }
        }
    }

    private List<String> safeZipParts(String entryName) {
        String normalized = entryName.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains("../") || normalized.contains("/..")) {
            throw AppException.badRequest("压缩包中存在非法路径");
        }
        List<String> parts = new ArrayList<>();
        for (String part : normalized.split("/")) {
            if (part.isBlank() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                throw AppException.badRequest("压缩包中存在非法路径");
            }
            parts.add(part);
        }
        return parts;
    }

    private boolean isZip(CloudFile file) {
        return "zip".equalsIgnoreCase(file.getExtension())
                || "application/zip".equalsIgnoreCase(file.getContentType())
                || "application/x-zip-compressed".equalsIgnoreCase(file.getContentType());
    }

    private void writeFolderToZip(CloudFile folder, String prefix, ZipOutputStream zipOutputStream) throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(prefix));
        zipOutputStream.closeEntry();
        List<CloudFile> children = fileRepository.findByParentIdAndDeletedFalseOrderByFileKindDescNameAsc(folder.getId());
        for (CloudFile child : children) {
            String entryName = prefix + zipPart(child.getName());
            if (child.getFileKind() == FileKind.FOLDER) {
                writeFolderToZip(child, entryName + "/", zipOutputStream);
            } else {
                writeFileToZip(child, entryName, zipOutputStream);
            }
        }
    }

    private void writeFileToZip(CloudFile file, String entryName, ZipOutputStream zipOutputStream) throws IOException {
        StorageObject object = requireObject(file);
        zipOutputStream.putNextEntry(new ZipEntry(entryName));
        try (InputStream inputStream = storageService.resource(object.getRelativePath()).getInputStream()) {
            inputStream.transferTo(zipOutputStream);
        }
        zipOutputStream.closeEntry();
    }

    private Resource tempZipResource(Path zipPath) throws IOException {
        InputStream inputStream = new FilterInputStream(Files.newInputStream(zipPath)) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    Files.deleteIfExists(zipPath);
                }
            }
        };
        return new InputStreamResource(inputStream);
    }

    private void deleteTempZip(Path zipPath) {
        try {
            Files.deleteIfExists(zipPath);
        } catch (IOException ignored) {
            // Best effort cleanup for failed archive creation.
        }
    }

    private String zipFilename(String name) {
        String base = zipPart(name);
        return base.toLowerCase().endsWith(".zip") ? base : base + ".zip";
    }

    private String zipPart(String name) {
        String cleaned = name == null ? "untitled" : name;
        cleaned = cleaned.replace('\\', '_').replace('/', '_').replaceAll("[\\p{Cntrl}]", "").trim();
        if (cleaned.isBlank() || ".".equals(cleaned) || "..".equals(cleaned)) {
            return "untitled";
        }
        return cleaned;
    }

    private MediaType mediaType(String contentType) {
        try {
            return MediaType.parseMediaType(contentType == null ? "application/octet-stream" : contentType);
        } catch (Exception ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private User attachedUser(User user) {
        return userRepository.getReferenceById(user.getId());
    }

    private <T> List<List<T>> batches(Collection<T> values, int batchSize) {
        if (values.isEmpty()) {
            return List.of();
        }
        List<T> items = new ArrayList<>(values);
        List<List<T>> batches = new ArrayList<>((items.size() + batchSize - 1) / batchSize);
        for (int start = 0; start < items.size(); start += batchSize) {
            batches.add(items.subList(start, Math.min(start + batchSize, items.size())));
        }
        return batches;
    }

    private long elapsedMs(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }

    public record DownloadPayload(Resource resource, String filename, MediaType mediaType, long sizeBytes) {
    }

    private ThreadFactory namedThreadFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + sequence.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }

    private record ExtractArchive(Long fileId, String name, Path archivePath) {
    }

    private record ZipEntryPlan(String name, boolean directory, long size, List<String> parts) {
    }

    private record ExtractPlan(Long rootId, List<PlannedExtractFile> files) {
    }

    private record PlannedExtractFile(
            int index,
            String entryName,
            long size,
            Long parentId,
            String name,
            String contentType) {
    }

    private record ExtractedObject(
            int index,
            Long parentId,
            String name,
            FileStorageService.StoredObject storedObject) {
    }

    private record ExtractedFileInsert(String name, Long parentId, StorageObject object) {
    }

    private StoredObjectKey storedObjectKey(FileStorageService.StoredObject storedObject) {
        return new StoredObjectKey(storedObject.sha256(), storedObject.size());
    }

    private StoredObjectKey storedObjectKey(StorageObject object) {
        return new StoredObjectKey(object.getSha256(), object.getSizeBytes());
    }

    private record StoredObjectKey(String sha256, long sizeBytes) {
    }

    private record ZipStats(int totalEntries, int totalFiles, long totalBytes) {
    }
}
