package com.planmate.auth.service;

import com.planmate.auth.entity.EmailVerification.Purpose;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 인증 관련 해시 (A-P09, ACC-01-02·03).
 * - 인증번호: HMAC-SHA256("code:" + 용도 + ":" + 이메일 + ":" + 번호). 저엔트로피 번호라 단순 해시로 보관하지 않는다.
 * - 요청 IP: HMAC-SHA256("ip:" + IP). IP 원문은 저장하지 않는다.
 * - 가입 확인값: SHA-256 (32바이트 난수라 HMAC 없이도 추측 불가).
 * 결과는 모두 hex 64자. 비교는 고정 시간(MessageDigest.isEqual).
 * javax.crypto는 JDK 표준 API다(금지된 Jakarta EE javax.*가 아님).
 */
@Component
public class AuthHasher {

    private static final String SECRET_PROPERTY = "planmate.auth.email-verification-hmac-secret";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final HexFormat HEX = HexFormat.of();

    private final byte[] secret;

    public AuthHasher(@Value("${" + SECRET_PROPERTY + "}") String secret) {
        if (secret == null || secret.isBlank()) {
            // 값은 남기지 않고 키 이름만
            throw new IllegalStateException(SECRET_PROPERTY + " 값이 비어 있음");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    /** 인증번호 HMAC (CODE_HMAC) */
    public String codeHmac(Purpose purpose, String email, String code) {
        return hmacHex("code:" + purpose.name() + ":" + email + ":" + code);
    }

    /** 요청 IP 해시 (REQUEST_IP_HASH) */
    public String ipHash(String ip) {
        return hmacHex("ip:" + (ip == null ? "" : ip));
    }

    /** 가입 확인값 해시 (CONFIRM_TOKEN_HASH) */
    public String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HEX.formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 사용 불가", e);
        }
    }

    /** 저장된 값과 고정 시간 비교. 둘 중 하나라도 null이면 false */
    public boolean matches(String stored, String candidate) {
        if (stored == null || candidate == null) {
            return false;
        }
        return MessageDigest.isEqual(stored.getBytes(StandardCharsets.UTF_8), candidate.getBytes(StandardCharsets.UTF_8));
    }

    // Mac은 스레드 안전하지 않아 호출할 때마다 새로 만든다
    private String hmacHex(String message) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return HEX.formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 사용 불가", e);
        }
    }
}
