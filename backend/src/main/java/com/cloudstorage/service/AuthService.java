package com.cloudstorage.service;

import com.cloudstorage.dto.AuthDtos.AuthResponse;
import com.cloudstorage.dto.AuthDtos.LoginRequest;
import com.cloudstorage.dto.AuthDtos.RegisterRequest;
import com.cloudstorage.model.Role;
import com.cloudstorage.model.User;
import com.cloudstorage.repository.UserRepository;
import com.cloudstorage.security.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,30}$");
    private static final DateTimeFormatter BAN_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.of("Asia/Shanghai"));

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final CaptchaService captchaService;
    private final UserMapper userMapper;
    private final AuditService auditService;
    private final SystemSettingsService systemSettingsService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            CaptchaService captchaService,
            UserMapper userMapper,
            AuditService auditService,
            SystemSettingsService systemSettingsService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.captchaService = captchaService;
        this.userMapper = userMapper;
        this.auditService = auditService;
        this.systemSettingsService = systemSettingsService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (!systemSettingsService.isUserLoginAllowed()) {
            throw AppException.forbidden("当前暂不允许普通用户注册和登录");
        }
        if (!systemSettingsService.isUserRegistrationAllowed()) {
            throw AppException.forbidden("当前暂不允许新账号注册");
        }
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw AppException.badRequest("用户名只能包含字母、数字、下划线，长度 3-30 位");
        }
        if (userRepository.existsByUsername(username)) {
            throw AppException.badRequest("用户名已存在");
        }
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setReserveEmail(request.reserveEmail().trim());
        user.setNickname(cleanNullable(request.nickname()));
        userRepository.save(user);
        return authResponse(user);
    }

    public AuthResponse login(LoginRequest request, HttpServletRequest servletRequest) {
        String username = request.username() == null ? "" : request.username().trim().toLowerCase(Locale.ROOT);
        User user = null;
        try {
            captchaService.validate(request.captchaId(), request.captchaCode());
            user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));
            if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                throw new AppException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
            }
            if (!user.isAccessAllowed()) {
                throw new AppException(HttpStatus.FORBIDDEN, accountStatusMessage(user));
            }
            if (user.getRole() != Role.ADMIN && !systemSettingsService.isUserLoginAllowed()) {
                throw AppException.forbidden("当前暂不允许普通用户登录");
            }
            AuthResponse response = authResponse(user);
            auditService.recordLogin(username, user, true, "登录成功", servletRequest);
            return response;
        } catch (AppException ex) {
            auditService.recordLogin(username, user, false, ex.getMessage(), servletRequest);
            throw ex;
        }
    }

    private AuthResponse authResponse(User user) {
        String token = tokenService.createToken(user.getId(), user.getUsername());
        return new AuthResponse(token, userMapper.toUserResponse(user));
    }

    private String cleanNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String accountStatusMessage(User user) {
        if (user.isDeleted()) {
            return "账号已被删除，请联系管理员";
        }
        if (!user.isEnabled()) {
            return "账号已被禁用，请联系管理员";
        }
        String reason = user.getBanReason() == null || user.getBanReason().isBlank()
                ? "未填写原因"
                : user.getBanReason();
        String until = user.getBannedUntil() == null
                ? "永久"
                : BAN_TIME_FORMATTER.format(user.getBannedUntil());
        return "账号已被封禁，原因：" + reason + "，封禁至：" + until;
    }
}
