package com.cloudstorage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class SystemSettingsDtos {
    private SystemSettingsDtos() {
    }

    public record PublicSettingsResponse(
            String siteName,
            boolean allowUserLogin,
            boolean allowUserRegistration,
            boolean allowAvatarChange) {
    }

    public record AdminSettingsResponse(
            Long id,
            String siteName,
            boolean allowUserLogin,
            boolean allowUserRegistration,
            boolean allowAvatarChange,
            Instant updatedAt) {
    }

    public record UpdateSystemSettingsRequest(
            @NotBlank @Size(max = 80) String siteName,
            @NotNull Boolean allowUserLogin,
            @NotNull Boolean allowUserRegistration,
            @NotNull Boolean allowAvatarChange) {
    }
}
