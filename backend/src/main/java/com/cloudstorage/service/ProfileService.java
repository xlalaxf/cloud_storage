package com.cloudstorage.service;

import com.cloudstorage.dto.AuthDtos.UserResponse;
import com.cloudstorage.dto.UserDtos.UpdateProfileRequest;
import com.cloudstorage.model.Role;
import com.cloudstorage.model.User;
import com.cloudstorage.repository.UserRepository;
import java.io.IOException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProfileService {
    private static final long MAX_AVATAR_UPLOAD_BYTES = 10L * 1024 * 1024;
    private static final int MAX_STORED_AVATAR_BYTES = 100 * 1024;

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final SystemSettingsService systemSettingsService;

    public ProfileService(
            UserRepository userRepository,
            UserMapper userMapper,
            SystemSettingsService systemSettingsService) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.systemSettingsService = systemSettingsService;
    }

    @Transactional
    public UserResponse update(User currentUser, UpdateProfileRequest request) {
        User user = userRepository.findById(currentUser.getId()).orElseThrow(() -> AppException.notFound("用户不存在"));
        user.setNickname(cleanNullable(request.nickname()));
        user.setReserveEmail(request.reserveEmail().trim());
        user.setPhone(cleanNullable(request.phone()));
        return userMapper.toUserResponse(user);
    }

    @Transactional
    public UserResponse uploadAvatar(User currentUser, MultipartFile avatar) {
        if (currentUser.getRole() != Role.ADMIN && !systemSettingsService.isAvatarChangeAllowed()) {
            throw AppException.forbidden("当前暂不允许普通用户修改头像");
        }
        if (avatar == null || avatar.isEmpty()) {
            throw AppException.badRequest("请选择头像文件");
        }
        if (avatar.getSize() > MAX_AVATAR_UPLOAD_BYTES) {
            throw AppException.badRequest("头像不能超过 10MB");
        }
        String contentType = avatar.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw AppException.badRequest("头像必须是图片文件");
        }
        try {
            byte[] bytes = avatar.getBytes();
            if (bytes.length > MAX_STORED_AVATAR_BYTES) {
                throw AppException.badRequest("头像压缩后不能超过 100KB");
            }
            User user = userRepository.findById(currentUser.getId())
                    .orElseThrow(() -> AppException.notFound("用户不存在"));
            user.setAvatarData(bytes);
            user.setAvatarContentType(contentType);
            return userMapper.toUserResponse(user);
        } catch (IOException ex) {
            throw AppException.badRequest("头像上传失败");
        }
    }

    @Transactional
    public UserResponse clearAvatar(User currentUser) {
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> AppException.notFound("用户不存在"));
        user.setAvatarData(null);
        user.setAvatarContentType(null);
        return userMapper.toUserResponse(user);
    }

    @Transactional(readOnly = true)
    public AvatarPayload avatar(User currentUser) {
        User user = userRepository.findById(currentUser.getId()).orElseThrow(() -> AppException.notFound("用户不存在"));
        if (user.getAvatarData() == null || user.getAvatarData().length == 0) {
            throw AppException.notFound("头像不存在");
        }
        String contentType = user.getAvatarContentType() == null ? "image/jpeg" : user.getAvatarContentType();
        return new AvatarPayload(user.getAvatarData(), contentType);
    }

    private String cleanNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record AvatarPayload(byte[] data, String contentType) {
    }
}
