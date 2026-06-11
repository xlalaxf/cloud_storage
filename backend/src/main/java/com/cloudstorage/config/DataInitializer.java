package com.cloudstorage.config;

import com.cloudstorage.model.Role;
import com.cloudstorage.model.SystemSettings;
import com.cloudstorage.model.User;
import com.cloudstorage.repository.SystemSettingsRepository;
import com.cloudstorage.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {
    @Bean
    CommandLineRunner initData(
            UserRepository userRepository,
            SystemSettingsRepository systemSettingsRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.site.default-name:Cloud Storage}") String defaultSiteName,
            @Value("${app.admin.default-username}") String username,
            @Value("${app.admin.default-password}") String password,
            @Value("${app.admin.default-email}") String email) {
        return args -> {
            systemSettingsRepository.findById(SystemSettings.SINGLETON_ID)
                    .orElseGet(() -> {
                        SystemSettings settings = new SystemSettings();
                        settings.setSiteName(cleanDefaultSiteName(defaultSiteName));
                        return systemSettingsRepository.save(settings);
                    });

            if (userRepository.findByUsername(username).isEmpty()) {
                User admin = new User();
                admin.setUsername(username);
                admin.setPasswordHash(passwordEncoder.encode(password));
                admin.setReserveEmail(email);
                admin.setNickname("系统管理员");
                admin.setRole(Role.ADMIN);
                userRepository.save(admin);
            }
        };
    }

    private String cleanDefaultSiteName(String value) {
        if (value == null || value.isBlank()) {
            return SystemSettings.DEFAULT_SITE_NAME;
        }
        return value.trim();
    }
}
