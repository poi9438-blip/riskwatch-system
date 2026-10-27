package com.riskwatch.security;

import com.riskwatch.domain.SourceSystem;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

// HomeFin·FinFlow 서버의 서비스 토큰 확인 (HS256, aud=riskwatch, 유효 기간 5분 이하)
// 서버마다 발급자와 비밀키가 짝으로 정해져 있어, 한 서버의 비밀키로는 다른 서버인 척할 수 없음
@Component
public class ServiceTokens {

    public static final String AUDIENCE = "riskwatch";
    public static final String HOMEFIN_ISSUER = "homefin-mate";
    public static final String FINFLOW_ISSUER = "finflow";
    // 발급 시각부터 만료 시각까지 최대 5분 (FinFlow 서비스 토큰과 같음)
    public static final long MAX_LIFETIME_MS = 5 * 60 * 1000L;

    // 발급자 → (요청 서버, 그 서버의 비밀키)
    private final Map<String, Issuer> issuers = new LinkedHashMap<>();

    private record Issuer(SourceSystem sourceSystem, SecretKey key) {
    }

    // 키가 32바이트보다 짧으면 시작 실패 (jjwt WeakKeyException), 세 비밀키 중 같은 값이 있어도 시작 실패
    public ServiceTokens(@Value("${app.service-token.homefin-secret}") String homefinSecret,
            @Value("${app.service-token.finflow-secret}") String finflowSecret,
            @Value("${app.operator.jwt-secret}") String operatorSecret) {
        // Set.of는 같은 값이 있으면 그 값을 메시지에 넣어 예외를 던지므로 쓰지 않음 (비밀키가 로그에 남음)
        if (Stream.of(homefinSecret, finflowSecret, operatorSecret).distinct().count() < 3) {
            throw new IllegalStateException(
                    "HOMEFIN_TOKEN_SECRET, FINFLOW_TOKEN_SECRET and OPERATOR_JWT_SECRET must all be different");
        }
        issuers.put(HOMEFIN_ISSUER, new Issuer(SourceSystem.HOMEFIN, key(homefinSecret)));
        issuers.put(FINFLOW_ISSUER, new Issuer(SourceSystem.FINFLOW, key(finflowSecret)));
    }

    private static SecretKey key(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // 유효한 서비스 토큰이면 요청 서버. 발급자마다 그 서버의 비밀키로만 확인
    public Optional<SourceSystem> sourceSystem(String token) {
        for (Map.Entry<String, Issuer> entry : issuers.entrySet()) {
            if (isValid(token, entry.getKey(), entry.getValue().key())) {
                return Optional.of(entry.getValue().sourceSystem());
            }
        }
        return Optional.empty();
    }

    // 서명·발급자·대상·만료·유효 기간을 모두 확인
    private static boolean isValid(String token, String issuer, SecretKey key) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .requireAudience(AUDIENCE)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Date issuedAt = claims.getIssuedAt();
            Date expiration = claims.getExpiration();
            return issuedAt != null && expiration != null
                    && expiration.getTime() - issuedAt.getTime() <= MAX_LIFETIME_MS;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
