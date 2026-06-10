package com.cloudstorage.service;

import com.cloudstorage.dto.AdminDtos.AdminUserResponse;
import com.cloudstorage.dto.AuthDtos.UserResponse;
import com.cloudstorage.model.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
    public UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getReserveEmail(),
                user.getPhone(),
                user.getRole(),
                user.isEnabled(),
                user.getAvatarData() != null && user.getAvatarData().length > 0,
                user.getCreatedAt());
    }

    public AdminUserResponse toAdminUserResponse(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getReserveEmail(),
                user.getRole(),
                user.isEnabled(),
                user.isDeleted(),
                user.getDeletedAt(),
                user.isBanned(),
                user.getBanReason(),
                user.getBannedUntil(),
                user.getCreatedAt());
    }
}
