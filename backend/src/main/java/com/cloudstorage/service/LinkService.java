package com.cloudstorage.service;

import com.cloudstorage.dto.FileDtos.DirectLinkResponse;
import com.cloudstorage.dto.FileDtos.PublicShareResponse;
import com.cloudstorage.dto.FileDtos.ShareLinkResponse;
import com.cloudstorage.model.CloudFile;
import com.cloudstorage.model.DirectLink;
import com.cloudstorage.model.FileKind;
import com.cloudstorage.model.ShareLink;
import com.cloudstorage.model.User;
import com.cloudstorage.repository.CloudFileRepository;
import com.cloudstorage.repository.DirectLinkRepository;
import com.cloudstorage.repository.ShareLinkRepository;
import com.cloudstorage.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LinkService {
    private final SecureRandom secureRandom = new SecureRandom();
    private final DirectLinkRepository directLinkRepository;
    private final ShareLinkRepository shareLinkRepository;
    private final CloudFileRepository fileRepository;
    private final UserRepository userRepository;
    private final FileService fileService;
    private final AuditService auditService;
    private final String frontendOrigin;

    public LinkService(
            DirectLinkRepository directLinkRepository,
            ShareLinkRepository shareLinkRepository,
            CloudFileRepository fileRepository,
            UserRepository userRepository,
            FileService fileService,
            AuditService auditService,
            @Value("${app.frontend-origin:http://127.0.0.1:5173}") String frontendOrigin) {
        this.directLinkRepository = directLinkRepository;
        this.shareLinkRepository = shareLinkRepository;
        this.fileRepository = fileRepository;
        this.userRepository = userRepository;
        this.fileService = fileService;
        this.auditService = auditService;
        this.frontendOrigin = stripTrailingSlash(frontendOrigin);
    }

    @Transactional
    public DirectLinkResponse createDirectLink(User user, Long fileId, Instant expiresAt, String requestOrigin) {
        CloudFile file = fileService.requireOwned(user, fileId);
        if (file.getFileKind() != FileKind.FILE) {
            throw AppException.badRequest("直链只能用于文件");
        }
        DirectLink link = new DirectLink();
        link.setOwner(userRepository.getReferenceById(user.getId()));
        link.setFile(file);
        link.setToken(randomToken());
        link.setExpiresAt(expiresAt);
        DirectLink saved = directLinkRepository.save(link);
        auditService.recordFileOperation(user, file, "CREATE_DIRECT_LINK", expiresAt == null ? "永久有效" : "到期: " + expiresAt);
        return toResponse(saved, requestOrigin);
    }

    @Transactional(readOnly = true)
    public List<DirectLinkResponse> listDirectLinks(User user, String requestOrigin) {
        return directLinkRepository.findByOwnerIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(link -> toResponse(link, requestOrigin))
                .toList();
    }

    @Transactional
    public void deleteDirectLink(User user, Long linkId) {
        DirectLink link = directLinkRepository.findById(linkId)
                .orElseThrow(() -> AppException.notFound("直链不存在"));
        if (!link.getOwner().getId().equals(user.getId())) {
            throw AppException.forbidden("不能删除该直链");
        }
        auditService.recordFileOperation(user, link.getFile(), "DELETE_DIRECT_LINK", "Token: " + link.getToken());
        directLinkRepository.delete(link);
    }

    @Transactional
    public FileService.DownloadPayload downloadDirect(String token) {
        DirectLink link = directLinkRepository.findByToken(token).orElseThrow(() -> AppException.notFound("直链不存在"));
        ensureLinkUsable(link.isEnabled(), link.getExpiresAt());
        link.setDownloadCount(link.getDownloadCount() + 1);
        FileService.DownloadPayload payload = fileService.download(link.getFile(), true);
        auditService.recordFileOperation(link.getOwner(), link.getFile(), "DIRECT_LINK_DOWNLOAD", "Token: " + link.getToken());
        return payload;
    }

    @Transactional
    public ShareLinkResponse createShare(
            User user,
            Long fileId,
            String extractionCode,
            Instant expiresAt,
            String requestOrigin) {
        CloudFile file = fileService.requireOwned(user, fileId);
        ShareLink link = new ShareLink();
        link.setOwner(userRepository.getReferenceById(user.getId()));
        link.setRootFile(file);
        link.setToken(randomToken());
        link.setExtractionCode(cleanCode(extractionCode));
        link.setExpiresAt(expiresAt);
        ShareLink saved = shareLinkRepository.save(link);
        auditService.recordFileOperation(user, file, "CREATE_SHARE", expiresAt == null ? "永久有效" : "到期: " + expiresAt);
        return toResponse(saved, requestOrigin);
    }

    @Transactional(readOnly = true)
    public List<ShareLinkResponse> listShares(User user, String requestOrigin) {
        return shareLinkRepository.findByOwnerIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(link -> toResponse(link, requestOrigin))
                .toList();
    }

    @Transactional
    public void deleteShare(User user, Long shareId) {
        ShareLink link = shareLinkRepository.findById(shareId)
                .orElseThrow(() -> AppException.notFound("分享不存在"));
        if (!link.getOwner().getId().equals(user.getId())) {
            throw AppException.forbidden("不能删除该分享");
        }
        auditService.recordFileOperation(user, link.getRootFile(), "DELETE_SHARE", "Token: " + link.getToken());
        shareLinkRepository.delete(link);
    }

    @Transactional(readOnly = true)
    public PublicShareResponse viewShare(String token, String code, Long parentId) {
        ShareLink link = shareLinkRepository.findByToken(token).orElseThrow(() -> AppException.notFound("分享不存在"));
        ensureLinkUsable(link.isEnabled(), link.getExpiresAt());
        boolean unlocked = isUnlocked(link, code);
        if (!unlocked) {
            return new PublicShareResponse(token, true, false, null, List.of(), link.getDownloadCount());
        }
        CloudFile root = link.getRootFile();
        if (root.isDeleted()) {
            throw AppException.notFound("分享文件不存在");
        }
        List<CloudFile> children;
        if (root.getFileKind() == FileKind.FILE) {
            children = List.of(root);
        } else {
            Long listParentId = parentId == null ? root.getId() : parentId;
            CloudFile parent = fileService.requireExisting(listParentId);
            if (parent.getFileKind() != FileKind.FOLDER || !fileService.isSameOrDescendant(parent, root)) {
                throw AppException.forbidden("不能访问该目录");
            }
            children = fileRepository.findByParentIdAndDeletedFalseOrderByFileKindDescNameAsc(listParentId);
        }
        return new PublicShareResponse(
                token,
                hasCode(link),
                true,
                fileService.toResponse(root),
                children.stream().map(fileService::toResponse).toList(),
                link.getDownloadCount());
    }

    @Transactional
    public FileService.DownloadPayload downloadShare(String token, String code, Long fileId) {
        ShareLink link = shareLinkRepository.findByToken(token).orElseThrow(() -> AppException.notFound("分享不存在"));
        ensureLinkUsable(link.isEnabled(), link.getExpiresAt());
        if (!isUnlocked(link, code)) {
            throw AppException.forbidden("提取码错误");
        }
        CloudFile target = fileService.requireExisting(fileId);
        if (!fileService.isSameOrDescendant(target, link.getRootFile())) {
            throw AppException.forbidden("不能下载该项目");
        }
        link.setDownloadCount(link.getDownloadCount() + 1);
        FileService.DownloadPayload payload = target.getFileKind() == FileKind.FOLDER
                ? fileService.downloadFolder(target, true)
                : fileService.download(target, true);
        auditService.recordFileOperation(link.getOwner(), target, "SHARE_DOWNLOAD", "分享: " + link.getToken());
        return payload;
    }

    private DirectLinkResponse toResponse(DirectLink link, String requestOrigin) {
        String url = originOrDefault(requestOrigin) + "/api/public/direct/" + link.getToken();
        return new DirectLinkResponse(
                link.getId(),
                link.getFile().getId(),
                link.getToken(),
                url,
                link.isEnabled(),
                link.getExpiresAt(),
                link.getDownloadCount(),
                link.getCreatedAt());
    }

    private ShareLinkResponse toResponse(ShareLink link, String requestOrigin) {
        String url = originOrDefault(requestOrigin) + "/share/" + link.getToken();
        return new ShareLinkResponse(
                link.getId(),
                link.getRootFile().getId(),
                link.getRootFile().getName(),
                link.getRootFile().getFileKind(),
                link.getToken(),
                url,
                hasCode(link),
                link.getExtractionCode(),
                link.isEnabled(),
                link.getExpiresAt(),
                link.getDownloadCount(),
                link.getCreatedAt());
    }

    private void ensureLinkUsable(boolean enabled, Instant expiresAt) {
        if (!enabled || (expiresAt != null && expiresAt.isBefore(Instant.now()))) {
            throw AppException.forbidden("链接已失效");
        }
    }

    private boolean isUnlocked(ShareLink link, String code) {
        if (!hasCode(link)) {
            return true;
        }
        return code != null && link.getExtractionCode().equalsIgnoreCase(code.trim());
    }

    private boolean hasCode(ShareLink link) {
        return link.getExtractionCode() != null && !link.getExtractionCode().isBlank();
    }

    private String cleanCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String cleaned = code.trim();
        if (cleaned.length() > 20) {
            throw AppException.badRequest("提取码不能超过 20 位");
        }
        return cleaned;
    }

    private String randomToken() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String originOrDefault(String requestOrigin) {
        if (requestOrigin == null || requestOrigin.isBlank()) {
            return frontendOrigin;
        }
        return stripTrailingSlash(requestOrigin);
    }

    private String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://127.0.0.1:5173";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
