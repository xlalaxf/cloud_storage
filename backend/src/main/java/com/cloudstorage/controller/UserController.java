package com.cloudstorage.controller;

import com.cloudstorage.dto.ApiResponse;
import com.cloudstorage.dto.AuthDtos.UserResponse;
import com.cloudstorage.dto.UserDtos.UpdateProfileRequest;
import com.cloudstorage.model.User;
import com.cloudstorage.service.ProfileService;
import com.cloudstorage.service.UserMapper;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final ProfileService profileService;
    private final UserMapper userMapper;

    public UserController(ProfileService profileService, UserMapper userMapper) {
        this.profileService = profileService;
        this.userMapper = userMapper;
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal User user) {
        return ApiResponse.ok(userMapper.toUserResponse(user));
    }

    @PutMapping("/me")
    public ApiResponse<UserResponse> update(@AuthenticationPrincipal User user, @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok("已保存", profileService.update(user, request));
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UserResponse> uploadAvatar(
            @AuthenticationPrincipal User user,
            @RequestParam("avatar") MultipartFile avatar) {
        return ApiResponse.ok("头像已更新", profileService.uploadAvatar(user, avatar));
    }

    @DeleteMapping("/me/avatar")
    public ApiResponse<UserResponse> clearAvatar(@AuthenticationPrincipal User user) {
        return ApiResponse.ok("已恢复默认头像", profileService.clearAvatar(user));
    }

    @GetMapping("/me/avatar")
    public ResponseEntity<byte[]> avatar(@AuthenticationPrincipal User user) {
        ProfileService.AvatarPayload avatar = profileService.avatar(user);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(avatar.contentType()))
                .body(avatar.data());
    }
}
