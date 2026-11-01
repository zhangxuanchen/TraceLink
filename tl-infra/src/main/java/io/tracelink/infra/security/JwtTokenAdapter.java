package io.tracelink.infra.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.tracelink.common.error.BizException;
import io.tracelink.common.error.ErrorCode;
import io.tracelink.domain.auth.AuthUser;
import io.tracelink.domain.auth.TokenPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

@Component
public class JwtTokenAdapter implements TokenPort {

    private final SecretKey key;
    private final long ttlMinutes;

    public JwtTokenAdapter(@Value("${tracelink.auth.jwt.secret}") String secret,
                           @Value("${tracelink.auth.jwt.ttl-minutes:480}") long ttlMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlMinutes = ttlMinutes;
    }

    @Override
    public String issue(AuthUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.employeeId()))
                .claim("uname", user.username())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttlMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    @Override
    public AuthUser parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            return new AuthUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get("uname", String.class),
                    null,
                    null,
                    List.of(),
                    null);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BizException(ErrorCode.UNAUTHENTICATED, "登录已过期，请重新登录");
        }
    }
}
