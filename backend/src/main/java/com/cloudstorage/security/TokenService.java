package com.cloudstorage.security;

import com.cloudstorage.service.AppException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
    private final byte[] secret;
    private final Duration expireDuration;

    public TokenService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expire-hours:168}") long expireHours) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expireDuration = Duration.ofHours(expireHours);
    }

    public String createToken(Long userId, String username) {
        long expiresAt = Instant.now().plus(expireDuration).getEpochSecond();
        String payload = userId + ":" + username + ":" + expiresAt;
        String encodedPayload = base64(payload.getBytes(StandardCharsets.UTF_8));
        String signature = base64(sign(encodedPayload));
        return encodedPayload + "." + signature;
    }

    public Long parseUserId(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 2) {
                throw unauthorized();
            }
            String expected = base64(sign(parts[0]));
            if (!constantTimeEquals(expected, parts[1])) {
                throw unauthorized();
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String[] values = payload.split(":", 3);
            if (values.length != 3 || Instant.now().getEpochSecond() > Long.parseLong(values[2])) {
                throw unauthorized();
            }
            return Long.parseLong(values[0]);
        } catch (RuntimeException ex) {
            if (ex instanceof AppException appException) {
                throw appException;
            }
            throw unauthorized();
        }
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("无法生成令牌签名", ex);
        }
    }

    private String base64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left.length() != right.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < left.length(); i++) {
            result |= left.charAt(i) ^ right.charAt(i);
        }
        return result == 0;
    }

    private AppException unauthorized() {
        return new AppException(HttpStatus.UNAUTHORIZED, "登录状态已失效");
    }
}
