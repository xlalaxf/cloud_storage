package com.cloudstorage.dto;

import com.cloudstorage.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 30) String username,
            @NotBlank @Size(min = 6, max = 72) String password,
            @NotBlank @Email @Size(max = 120) String reserveEmail,
            @Size(max = 60) String nickname) {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password,
            @NotBlank String captchaId,
            @NotBlank String captchaCode) {
    }

    public record CaptchaResponse(String captchaId, String svg) {
    }

    public record AuthResponse(String token, UserResponse user) {
    }

    public record UserResponse(
            Long id,
            String username,
            String nickname,
            String reserveEmail,
            String phone,
            Role role,
            boolean enabled,
            boolean hasAvatar,
            Instant createdAt) {
    }
}
