package com.cloudstorage.security;

import com.cloudstorage.model.User;
import com.cloudstorage.repository.UserRepository;
import com.cloudstorage.service.SystemSettingsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final SystemSettingsService systemSettingsService;

    public JwtAuthenticationFilter(
            TokenService tokenService,
            UserRepository userRepository,
            SystemSettingsService systemSettingsService) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
        this.systemSettingsService = systemSettingsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            Long userId = tokenService.parseUserId(header.substring(7));
            userRepository.findById(userId)
                    .filter(User::isAccessAllowed)
                    .filter(user -> user.isAdmin() || systemSettingsService.isUserLoginAllowed())
                    .ifPresent(user -> {
                var authority = new SimpleGrantedAuthority("ROLE_" + user.getRole().name());
                var authentication = new UsernamePasswordAuthenticationToken(user, null, List.of(authority));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }
        filterChain.doFilter(request, response);
    }
}
