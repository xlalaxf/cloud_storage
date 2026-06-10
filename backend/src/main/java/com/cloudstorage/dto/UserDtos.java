package com.cloudstorage.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class UserDtos {
    private UserDtos() {
    }

    public record UpdateProfileRequest(
            @Size(max = 60) String nickname,
            @NotBlank @Email @Size(max = 120) String reserveEmail,
            @Size(max = 30) String phone) {
    }
}
