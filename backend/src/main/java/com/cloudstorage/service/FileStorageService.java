package com.cloudstorage.service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {
    private final Path root;

    public FileStorageService(@Value("${storage.root:storage}") String root) {
        this.root = Paths.get(root).toAbsolutePath().normalize();
    }

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(root);
    }

    public StoredObject store(MultipartFile file, Long userId, String safeName) {
        try (InputStream inputStream = file.getInputStream()) {
            String contentType = file.getContentType();
            return prepareObject(inputStream, safeName, contentType);
        } catch (IOException ex) {
            throw AppException.badRequest("文件保存失败");
        }
    }

    public StoredObject storeStream(InputStream inputStream, Long userId, String safeName, String contentType) {
        return prepareObject(inputStream, safeName, contentType);
    }

    public StoredChunk storeUploadChunk(String uploadId, int chunkIndex, InputStream inputStream) {
        Path temp = null;
        try {
            Path relative = Paths.get("tmp", "uploads", uploadId, "chunk-" + chunkIndex + ".part");
            Path target = safeResolve(relative);
            Files.createDirectories(target.getParent());
            temp = Files.createTempFile(target.getParent(), "chunk-", ".tmp");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size;
            try (DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
                size = Files.copy(digestInputStream, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            temp = null;
            return new StoredChunk(normalizePath(relative), size, hex(digest.digest()));
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw AppException.badRequest("分片保存失败");
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // Best effort cleanup for failed chunk writes.
                }
            }
        }
    }

    public StoredObject prepareObject(InputStream inputStream, String safeName, String contentType) {
        Path temp = null;
        try {
            Files.createDirectories(root.resolve("tmp"));
            temp = Files.createTempFile(root.resolve("tmp"), "upload-", ".tmp");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size;
            try (DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
                size = Files.copy(digestInputStream, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            String sha256 = hex(digest.digest());
            String extension = extensionOf(safeName);
            String storedName = sha256;
            Path relative = Paths.get(
                    "objects",
                    sha256.substring(0, 2),
                    sha256.substring(2, 4),
                    storedName);
            Path target = safeResolve(relative);
            Files.createDirectories(target.getParent());
            boolean created = !Files.exists(target);
            if (created) {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
                temp = null;
            }
            String resolvedContentType = contentType;
            if (resolvedContentType == null || resolvedContentType.isBlank()) {
                resolvedContentType = URLConnection.guessContentTypeFromName(safeName);
            }
            if (resolvedContentType == null || resolvedContentType.isBlank()) {
                resolvedContentType = "application/octet-stream";
            }
            return new StoredObject(storedName, normalizePath(relative), resolvedContentType, extension, size, sha256, created);
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw AppException.badRequest("文件保存失败");
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // Best effort cleanup for failed or deduplicated uploads.
                }
            }
        }
    }

    public StoredObject copyExisting(String relativePath, Long userId, String safeName, String contentType) {
        try (InputStream inputStream = Files.newInputStream(safeResolve(Paths.get(relativePath)))) {
            return storeStream(inputStream, userId, safeName, contentType);
        } catch (IOException ex) {
            throw AppException.badRequest("文件复制失败");
        }
    }

    public void deleteObject(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(safeResolve(Paths.get(relativePath)));
        } catch (IOException ex) {
            throw AppException.badRequest("文件删除失败");
        }
    }

    public void deleteDirectory(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        try {
            Path target = safeResolve(Paths.get(relativePath));
            if (!Files.exists(target)) {
                return;
            }
            try (Stream<Path> paths = Files.walk(target)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ex) {
                        throw new IllegalStateException(ex);
                    }
                });
            }
        } catch (IllegalStateException | IOException ex) {
            throw AppException.badRequest("临时文件清理失败");
        }
    }

    public Resource resource(String relativePath) {
        try {
            Path path = safeResolve(Paths.get(relativePath));
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw AppException.notFound("文件不存在");
            }
            return resource;
        } catch (IOException ex) {
            throw AppException.notFound("文件不存在");
        }
    }

    public Path createTempFile(String prefix, String suffix) {
        try {
            Path tempRoot = root.resolve("tmp");
            Files.createDirectories(tempRoot);
            return Files.createTempFile(tempRoot, prefix, suffix);
        } catch (IOException ex) {
            throw AppException.badRequest("临时文件创建失败");
        }
    }

    public String sanitizeName(String originalName) {
        String name = originalName == null ? "untitled" : originalName;
        name = name.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\p{Cntrl}]", "").trim();
        name = name.replaceAll("[<>:\"|?*]", "_");
        if (name.isBlank() || ".".equals(name) || "..".equals(name)) {
            name = "untitled";
        }
        return name.length() > 180 ? name.substring(0, 180) : name;
    }

    public String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private Path safeResolve(Path relative) throws IOException {
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            throw AppException.badRequest("非法文件路径");
        }
        return target;
    }

    private String normalizePath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) {
            builder.append("%02x".formatted(item));
        }
        return builder.toString();
    }

    public record StoredObject(
            String storedName,
            String relativePath,
            String contentType,
            String extension,
            long size,
            String sha256,
            boolean created) {
    }

    public record StoredChunk(String relativePath, long size, String sha256) {
    }
}
