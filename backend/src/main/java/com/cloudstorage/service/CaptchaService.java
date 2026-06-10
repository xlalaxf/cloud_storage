package com.cloudstorage.service;

import com.cloudstorage.dto.AuthDtos.CaptchaResponse;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CaptchaService {
    private static final String CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private final SecureRandom random = new SecureRandom();
    private final Map<String, CaptchaItem> cache = new ConcurrentHashMap<>();

    public CaptchaResponse createCaptcha() {
        cleanupExpired();
        String code = randomCode();
        String id = UUID.randomUUID().toString();
        cache.put(id, new CaptchaItem(code, Instant.now().plusSeconds(300)));
        return new CaptchaResponse(id, svgFor(code));
    }

    public void validate(String id, String code) {
        CaptchaItem item = cache.remove(id);
        if (item == null || item.expiresAt().isBefore(Instant.now()) || !item.code().equalsIgnoreCase(code.trim())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "验证码错误或已过期");
        }
    }

    private String randomCode() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            builder.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return builder.toString();
    }

    private String svgFor(String code) {
        int lineA = 10 + random.nextInt(25);
        int lineB = 15 + random.nextInt(30);
        return """
                <svg xmlns="http://www.w3.org/2000/svg" width="130" height="44" viewBox="0 0 130 44">
                  <rect width="130" height="44" rx="6" fill="#f6f8fb"/>
                  <path d="M5 %d C38 7 78 50 125 %d" stroke="#94a3b8" stroke-width="2" fill="none"/>
                  <path d="M0 %d L130 %d" stroke="#f97316" stroke-width="1" opacity=".7"/>
                  <text x="18" y="30" font-family="Consolas, monospace" font-size="24" font-weight="700" letter-spacing="3" fill="#0f172a">%s</text>
                </svg>
                """.formatted(lineA, lineB, lineB, lineA, code);
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        cache.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private record CaptchaItem(String code, Instant expiresAt) {
    }
}
