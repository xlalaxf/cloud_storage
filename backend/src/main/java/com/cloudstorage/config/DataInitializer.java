package com.cloudstorage.config;

import com.cloudstorage.model.Role;
import com.cloudstorage.model.User;
import com.cloudstorage.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {
    @Bean
    CommandLineRunner initAdmin(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.default-username}") String username,
            @Value("${app.admin.default-password}") String password,
            @Value("${app.admin.default-email}") String email) {
        return args -> {
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
}
