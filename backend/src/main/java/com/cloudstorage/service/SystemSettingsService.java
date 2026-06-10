package com.cloudstorage.service;

import com.cloudstorage.dto.SystemSettingsDtos.AdminSettingsResponse;
import com.cloudstorage.dto.SystemSettingsDtos.PublicSettingsResponse;
import com.cloudstorage.dto.SystemSettingsDtos.UpdateSystemSettingsRequest;
import com.cloudstorage.model.SystemSettings;
import com.cloudstorage.repository.SystemSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemSettingsService {
    private final SystemSettingsRepository systemSettingsRepository;

    public SystemSettingsService(SystemSettingsRepository systemSettingsRepository) {
        this.systemSettingsRepository = systemSettingsRepository;
    }

    @Transactional
    public PublicSettingsResponse publicSettings() {
        return toPublicResponse(currentSettings());
    }

    @Transactional
    public AdminSettingsResponse adminSettings() {
        return toAdminResponse(currentSettings());
    }

    @Transactional
    public AdminSettingsResponse update(UpdateSystemSettingsRequest request) {
        SystemSettings settings = currentSettings();
        settings.setSiteName(cleanSiteName(request.siteName()));
        settings.setAllowUserLogin(request.allowUserLogin());
        settings.setAllowUserRegistration(request.allowUserRegistration());
        settings.setAllowAvatarChange(request.allowAvatarChange());
        return toAdminResponse(settings);
    }

    @Transactional
    public boolean isUserLoginAllowed() {
        return currentSettings().isAllowUserLogin();
    }

    @Transactional
    public boolean isUserRegistrationAllowed() {
        return currentSettings().isAllowUserRegistration();
    }

    @Transactional
    public boolean isAvatarChangeAllowed() {
        return currentSettings().isAllowAvatarChange();
    }

    private SystemSettings currentSettings() {
        return systemSettingsRepository.findById(SystemSettings.SINGLETON_ID)
                .orElseGet(() -> systemSettingsRepository.save(new SystemSettings()));
    }

    private String cleanSiteName(String siteName) {
        String value = siteName == null ? "" : siteName.trim();
        if (value.isBlank()) {
            throw AppException.badRequest("网站名称不能为空");
        }
        return value;
    }

    private PublicSettingsResponse toPublicResponse(SystemSettings settings) {
        return new PublicSettingsResponse(
                settings.getSiteName(),
                settings.isAllowUserLogin(),
                settings.isAllowUserRegistration(),
                settings.isAllowAvatarChange());
    }

    private AdminSettingsResponse toAdminResponse(SystemSettings settings) {
        return new AdminSettingsResponse(
                settings.getId(),
                settings.getSiteName(),
                settings.isAllowUserLogin(),
                settings.isAllowUserRegistration(),
                settings.isAllowAvatarChange(),
                settings.getUpdatedAt());
    }
}
