package com.example.syncforge.auth;

import com.example.syncforge.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {
    //JWT 签名密钥
    @Value("${app.jwt.secret:syncforge-default-secret-key-change-me-123456}")
    private String jwtSecret;
    // access token 过期时间
    @Value("${app.jwt.expire-ms:86400000}")
    private long expireMs;
    // refresh token 过期时间（默认7天）
    @Value("${app.jwt.refresh-expire-ms:604800000}")
    private long refreshExpireMs;

    private static final String CLAIM_TOKEN_TYPE = "tokenType";
    private static final String CLAIM_REFRESH_JTI = "jti";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    //用来生成和验证 token 的 HMAC 密钥
    private SecretKey key;

    @PostConstruct
    public void init() {
        byte[] bytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(
                bytes.length >= 32 ? bytes : (jwtSecret + "-padding-to-32-bytes").getBytes(StandardCharsets.UTF_8)
        );
    }

    // 兼容旧调用：默认签发 access token
    public String generateToken(User user) {
        return generateAccessToken(user);
    }

    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expireMs);
        return Jwts.builder()
                .setSubject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken(User user, String tokenJti) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshExpireMs);
        return Jwts.builder()
                .setSubject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
                .claim(CLAIM_REFRESH_JTI, tokenJti)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // 解析 access token 的 userId
    public Long parseUserId(String token) {
        Claims claims = parseClaims(token);
        String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
        if (TOKEN_TYPE_REFRESH.equals(tokenType)) {
            throw new IllegalArgumentException("Refresh token cannot be used as access token");
        }
        return Long.valueOf(claims.getSubject());
    }

    public String parseUsername(String token) {
        Claims claims = parseClaims(token);
        return String.valueOf(claims.get("username", String.class));
    }

    public RefreshTokenClaims parseRefreshToken(String token) {
        Claims claims = parseClaims(token);
        String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
        if (!TOKEN_TYPE_REFRESH.equals(tokenType)) {
            throw new IllegalArgumentException("Invalid refresh token type");
        }
        String tokenJti = claims.get(CLAIM_REFRESH_JTI, String.class);
        if (tokenJti == null || tokenJti.trim().isEmpty()) {
            throw new IllegalArgumentException("Refresh token jti missing");
        }
        return new RefreshTokenClaims(
                Long.valueOf(claims.getSubject()),
                tokenJti,
                claims.getExpiration()
        );
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public static class RefreshTokenClaims {
        private final Long userId;
        private final String tokenJti;
        private final Date expiration;

        public RefreshTokenClaims(Long userId, String tokenJti, Date expiration) {
            this.userId = userId;
            this.tokenJti = tokenJti;
            this.expiration = expiration;
        }

        public Long getUserId() {
            return userId;
        }

        public String getTokenJti() {
            return tokenJti;
        }

        public Date getExpiration() {
            return expiration;
        }
    }
}
