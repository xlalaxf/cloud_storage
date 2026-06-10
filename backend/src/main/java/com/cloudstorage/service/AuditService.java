package com.cloudstorage.service;

import com.cloudstorage.dto.AdminDtos.FileOperationAuditResponse;
import com.cloudstorage.dto.AdminDtos.AuditClearResponse;
import com.cloudstorage.dto.AdminDtos.LoginAuditResponse;
import com.cloudstorage.model.CloudFile;
import com.cloudstorage.model.FileOperationAudit;
import com.cloudstorage.model.LoginAudit;
import com.cloudstorage.model.User;
import com.cloudstorage.repository.FileOperationAuditRepository;
import com.cloudstorage.repository.LoginAuditRepository;
import com.cloudstorage.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private static final int LOGIN_AUDIT_LIMIT = 30;
    private static final int FILE_OPERATION_AUDIT_LIMIT = 80;

    private final LoginAuditRepository loginAuditRepository;
    private final FileOperationAuditRepository fileOperationAuditRepository;
    private final UserRepository userRepository;

    public AuditService(
            LoginAuditRepository loginAuditRepository,
            FileOperationAuditRepository fileOperationAuditRepository,
            UserRepository userRepository) {
        this.loginAuditRepository = loginAuditRepository;
        this.fileOperationAuditRepository = fileOperationAuditRepository;
        this.userRepository = userRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLogin(String username, User user, boolean successful, String message, HttpServletRequest request) {
        LoginAudit audit = new LoginAudit();
        audit.setUsername(username == null || username.isBlank() ? "unknown" : username);
        audit.setUser(user == null ? null : userRepository.getReferenceById(user.getId()));
        audit.setSuccessful(successful);
        audit.setMessage(truncate(message, 300));
        audit.setIpAddress(resolveIp(request));
        loginAuditRepository.save(audit);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFileOperation(User user, CloudFile file, String operation, String detail) {
        recordFileOperation(
                user,
                file == null ? null : file.getId(),
                file == null ? null : file.getName(),
                operation,
                detail);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFileOperation(User user, Long fileId, String fileName, String operation, String detail) {
        if (user == null) {
            return;
        }
        writeFileOperation(user.getId(), fileId, fileName, operation, detail);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFileOperation(Long userId, Long fileId, String fileName, String operation, String detail) {
        writeFileOperation(userId, fileId, fileName, operation, detail);
    }

    private void writeFileOperation(Long userId, Long fileId, String fileName, String operation, String detail) {
        if (userId == null) {
            return;
        }
        User attachedUser = userRepository.findById(userId).orElse(null);
        if (attachedUser == null) {
            return;
        }
        FileOperationAudit audit = new FileOperationAudit();
        audit.setUser(attachedUser);
        audit.setUsername(attachedUser.getUsername());
        audit.setFileId(fileId);
        audit.setFileName(truncate(fileName, 180));
        audit.setOperation(operation);
        audit.setDetail(truncate(detail, 500));
        fileOperationAuditRepository.save(audit);
    }

    @Transactional(readOnly = true)
    public List<LoginAuditResponse> listLoginAudits(Long userId) {
        return listLoginAudits(userId, null, null);
    }

    @Transactional(readOnly = true)
    public List<LoginAuditResponse> listLoginAudits(Long userId, Instant from, Instant to) {
        List<LoginAudit> audits = from == null && to == null
                ? loginAuditRepository.findTop30ByUserIdOrderByCreatedAtDesc(userId)
                : loginAuditRepository.findTop30ByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                        userId,
                        from == null ? Instant.EPOCH : from,
                        to == null ? Instant.now() : to);
        return audits
                .stream()
                .limit(LOGIN_AUDIT_LIMIT)
                .map(this::toLoginResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FileOperationAuditResponse> listFileOperationAudits(Long userId) {
        return listFileOperationAudits(userId, null, null);
    }

    @Transactional(readOnly = true)
    public List<FileOperationAuditResponse> listFileOperationAudits(Long userId, Instant from, Instant to) {
        List<FileOperationAudit> audits = from == null && to == null
                ? fileOperationAuditRepository.findTop80ByUserIdOrderByCreatedAtDesc(userId)
                : fileOperationAuditRepository.findTop80ByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                        userId,
                        from == null ? Instant.EPOCH : from,
                        to == null ? Instant.now() : to);
        return audits
                .stream()
                .limit(FILE_OPERATION_AUDIT_LIMIT)
                .map(this::toFileOperationResponse)
                .toList();
    }

    @Transactional
    public AuditClearResponse clearAllAudits() {
        long loginAuditCount = loginAuditRepository.count();
        long fileOperationAuditCount = fileOperationAuditRepository.count();
        fileOperationAuditRepository.deleteAllInBatch();
        loginAuditRepository.deleteAllInBatch();
        return new AuditClearResponse(loginAuditCount, fileOperationAuditCount);
    }

    private LoginAuditResponse toLoginResponse(LoginAudit audit) {
        return new LoginAuditResponse(
                audit.getId(),
                audit.getUsername(),
                audit.getIpAddress(),
                audit.isSuccessful(),
                audit.getMessage(),
                audit.getCreatedAt());
    }

    private FileOperationAuditResponse toFileOperationResponse(FileOperationAudit audit) {
        return new FileOperationAuditResponse(
                audit.getId(),
                audit.getUserId(),
                audit.getUsername(),
                audit.getFileId(),
                audit.getFileName(),
                audit.getOperation(),
                audit.getDetail(),
                audit.getCreatedAt());
    }

    private String resolveIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return truncate(forwarded.split(",")[0].trim(), 80);
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return truncate(realIp.trim(), 80);
        }
        return truncate(request.getRemoteAddr(), 80);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
