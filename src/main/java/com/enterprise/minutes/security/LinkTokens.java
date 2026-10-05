package com.enterprise.minutes.security;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 分发链接令牌：仅用于证明"这封提醒邮件是发给你的"，不可猜测、不可转发给他人。
 * 令牌【不是】访问凭证——每次打开仍由 AccessService 实时核验岗位与授权状态。
 * 格式：base64url(minutesId.employeeId.issuedAtEpoch).hmacSHA256
 */
@ApplicationScoped
public class LinkTokens {

    @ConfigProperty(name = "app.token-secret", defaultValue = "demo-local-secret-change-me")
    String secret;

    public String issue(long minutesId, long employeeId, long issuedAtEpoch) {
        String payload = minutesId + "." + employeeId + "." + issuedAtEpoch;
        return enc(payload) + "." + hmac(payload);
    }

    /** 校验签名并解析；任何篡改/过期均返回 null。 */
    public long[] parse(String token, long maxAgeSeconds) {
        if (token == null || token.isBlank()) {
            return null;
        }
        // 令牌恰好两段：base64url(minutesId.employeeId.issuedAt) + "." + hmac
        int dot = token.indexOf('.');
        if (dot <= 0 || dot >= token.length() - 1 || token.indexOf('.', dot + 1) >= 0) {
            return null;
        }
        String payloadB64 = token.substring(0, dot);
        String sig = token.substring(dot + 1);
        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(payloadB64), StandardCharsets.UTF_8);
            String expectedSig = hmac(payload);
            if (!constantTimeEquals(sig, expectedSig)) {
                return null;
            }
            String[] parts = payload.split("\\.");
            if (parts.length != 3) {
                return null;
            }
            long minutesId = Long.parseLong(parts[0]);
            long employeeId = Long.parseLong(parts[1]);
            long issuedAt = Long.parseLong(parts[2]);
            long age = (System.currentTimeMillis() / 1000) - issuedAt;
            if (age > maxAgeSeconds || age < -300) {
                return null;
            }
            return new long[]{minutesId, employeeId, issuedAt};
        } catch (Exception e) {
            return null;
        }
    }

    private static String enc(String raw) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] h = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(h);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 不可用", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < a.length(); i++) {
            r |= a.charAt(i) ^ b.charAt(i);
        }
        return r == 0;
    }
}
