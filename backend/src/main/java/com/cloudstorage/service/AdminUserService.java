package com.cloudstorage.service;

import com.cloudstorage.dto.AdminDtos.AdminUserResponse;
import com.cloudstorage.dto.AdminDtos.BanUserRequest;
import com.cloudstorage.model.User;
import com.cloudstorage.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public AdminUserService(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> listUsers() {
        return userRepository.findByDeletedFalseOrderByCreatedAtAsc().stream()
                .map(userMapper::toAdminUserResponse)
                .toList();
    }

    @Transactional
    public AdminUserResponse banUser(User admin, Long userId, BanUserRequest request) {
        if (admin.getId().equals(userId)) {
            throw AppException.badRequest("不能封禁当前登录的管理员账号");
        }
        User user = requireActiveUser(userId);
        if (request.bannedUntil() != null && !request.bannedUntil().isAfter(Instant.now())) {
            throw AppException.badRequest("封禁截止时间必须晚于当前时间");
        }
        user.setBanReason(request.reason().trim());
        user.setBannedUntil(request.bannedUntil());
        return userMapper.toAdminUserResponse(user);
    }

    @Transactional
    public AdminUserResponse unbanUser(Long userId) {
        User user = requireActiveUser(userId);
        user.setBanReason(null);
        user.setBannedUntil(null);
        return userMapper.toAdminUserResponse(user);
    }

    @Transactional
    public void deleteUser(User admin, Long userId) {
        if (admin.getId().equals(userId)) {
            throw AppException.badRequest("不能删除当前登录的管理员账号");
        }
        User user = requireActiveUser(userId);
        user.setDeleted(true);
        user.setDeletedAt(Instant.now());
        user.setEnabled(false);
        user.setBanReason(null);
        user.setBannedUntil(null);
    }

    private User requireActiveUser(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> AppException.notFound("用户不存在"));
        if (user.isDeleted()) {
            throw AppException.notFound("用户不存在");
        }
        return user;
    }
}
