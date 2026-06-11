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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileService {
    private static final int ZIP_ENTRY_LIMIT = 10000;
    private static final long ZIP_EXTRACTED_SIZE_LIMIT = 3L * 1024 * 1024 * 1024;

    private final CloudFileRepository fileRepository;
    private final StorageObjectRepository objectRepository;
    private final UserRepository userRepository;
    private final FileStorageService storageService;
    private final FileMapper fileMapper;
    private final AuditService auditService;

    public FileService(
            CloudFileRepository fileRepository,
            StorageObjectRepository objectRepository,
            UserRepository userRepository,
            FileStorageService storageService,
            FileMapper fileMapper,
            AuditService auditService) {
        this.fileRepository = fileRepository;
        this.objectRepository = objectRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.fileMapper = fileMapper;
        this.auditService = auditService;
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

    @Transactional
    public List<FileResponse> extractZip(User user, Long fileId) {
        CloudFile archive = requireOwned(user, fileId);
        if (archive.getFileKind() != FileKind.FILE || !isZip(archive)) {
            throw AppException.badRequest("当前仅支持 ZIP 文件在线解压");
        }
        CloudFile extractRoot = createExtractRoot(user, archive);
        List<FileResponse> extracted = new ArrayList<>();
        List<FileStorageService.StoredObject> storedObjects = new ArrayList<>();
        try (InputStream inputStream = storageService.resource(archive.getRelativePath()).getInputStream();
                ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            int entries = 0;
            long extractedBytes = 0;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (++entries > ZIP_ENTRY_LIMIT) {
                    throw AppException.badRequest("压缩包文件数量超过限制");
                }
                long entrySize = entry.getSize();
                if (!entry.isDirectory() && entrySize > 0) {
                    extractedBytes += entrySize;
                    if (extractedBytes > ZIP_EXTRACTED_SIZE_LIMIT) {
                        throw AppException.badRequest("压缩包解压后体积超过限制");
                    }
                }
                List<String> parts = safeZipParts(entry.getName());
                if (parts.isEmpty()) {
                    continue;
                }
                CloudFile parent = ensureZipParent(user, extractRoot, parts.subList(0, parts.size() - 1));
                String name = storageService.sanitizeName(parts.get(parts.size() - 1));
                if (entry.isDirectory()) {
                    ensureFolder(user, parent, name);
                } else {
                    String uniqueName = uniqueName(user.getId(), parent.getId(), name);
                    String contentType = URLConnection.guessContentTypeFromName(uniqueName);
                    FileStorageService.StoredObject stored = storageService.storeOpenStream(
                            zipInputStream, user.getId(), uniqueName, contentType);
                    storedObjects.add(stored);
                    if (entrySize <= 0) {
                        extractedBytes += stored.size();
                        if (extractedBytes > ZIP_EXTRACTED_SIZE_LIMIT) {
                            throw AppException.badRequest("压缩包解压后体积超过限制");
                        }
                    }
                    StorageObject object = resolveObject(stored);
                    CloudFile extractedFile = buildFile(attachedUser(user), parent, uniqueName, object);
                    extracted.add(fileMapper.toResponse(fileRepository.save(extractedFile)));
                }
                zipInputStream.closeEntry();
            }
            extracted.add(0, fileMapper.toResponse(extractRoot));
            auditService.recordFileOperation(user, extractRoot, "EXTRACT", "来源压缩包: " + archive.getName());
            return extracted;
        } catch (Exception ex) {
            if (ex instanceof AppException appException) {
                cleanupCreatedObjects(storedObjects);
                throw appException;
            }
            cleanupCreatedObjects(storedObjects);
            throw AppException.badRequest("解压失败，请确认压缩包未损坏");
        }
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
                    StorageObject created = new StorageObject();
                    created.setSha256(stored.sha256());
                    created.setSizeBytes(stored.size());
                    created.setStoredName(stored.storedName());
                    created.setRelativePath(stored.relativePath());
                    created.setContentType(stored.contentType());
                    created.setExtension(stored.extension());
                    return created;
                });
        retainObject(object);
        return objectRepository.save(object);
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

    private CloudFile ensureZipParent(User user, CloudFile root, List<String> folderParts) {
        CloudFile parent = root;
        for (String rawPart : folderParts) {
            String part = storageService.sanitizeName(rawPart);
            parent = ensureFolder(user, parent, part);
        }
        return parent;
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

    private CloudFile ensureFolder(User user, CloudFile parent, String name) {
        Long parentId = parent.getId();
        return fileRepository.findFirstByOwnerIdAndParentIdAndNameAndFileKindAndDeletedFalse(
                        user.getId(), parentId, name, FileKind.FOLDER)
                .orElseGet(() -> {
                    CloudFile folder = new CloudFile();
                    folder.setOwner(attachedUser(user));
                    folder.setParent(parent);
                    folder.setFileKind(FileKind.FOLDER);
                    folder.setName(uniqueName(user.getId(), parentId, name));
                    return fileRepository.save(folder);
                });
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

    public record DownloadPayload(Resource resource, String filename, MediaType mediaType, long sizeBytes) {
    }
}
