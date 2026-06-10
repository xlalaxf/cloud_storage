package com.cloudstorage.controller;

import com.cloudstorage.dto.ApiResponse;
import com.cloudstorage.dto.AuthDtos.AuthResponse;
import com.cloudstorage.dto.AuthDtos.CaptchaResponse;
import com.cloudstorage.dto.AuthDtos.LoginRequest;
import com.cloudstorage.dto.AuthDtos.RegisterRequest;
import com.cloudstorage.service.AuthService;
import com.cloudstorage.service.CaptchaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final CaptchaService captchaService;

    public AuthController(AuthService authService, CaptchaService captchaService) {
        this.authService = authService;
        this.captchaService = captchaService;
    }

    @GetMapping("/captcha")
    public ApiResponse<CaptchaResponse> captcha() {
        return ApiResponse.ok(captchaService.createCaptcha());
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok("注册成功", authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {
        return ApiResponse.ok("登录成功", authService.login(request, servletRequest));
    }
}
