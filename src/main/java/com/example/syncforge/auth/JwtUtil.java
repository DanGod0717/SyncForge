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
    // 过期时间
    @Value("${app.jwt.expire-ms:86400000}")
    private long expireMs;
    //用来生成和验证 token 的 HMAC 密钥
    private SecretKey key;
    //@PostConstruct → Spring 初始化 Bean 后执行
    //HS256 要求密钥至少 32 字节
    //如果密钥不足 32 字节，就补齐（保证安全）
    @PostConstruct
    public void init() {
        byte[] bytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        // HMAC key must be >= 32 bytes for HS256.
        this.key = Keys.hmacShaKeyFor(bytes.length >= 32 ? bytes : (jwtSecret + "-padding-to-32-bytes").getBytes(StandardCharsets.UTF_8));
    }
    // 产生token
    public String generateToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expireMs);

        return Jwts.builder()
                .setSubject(String.valueOf(user.getId())) // 将用户ID作为主题
                .claim("username", user.getUsername())  // 额外信息
                .setIssuedAt(now)   // 签发时间
                .setExpiration(expiry)  //过期时间
                .signWith(key, SignatureAlgorithm.HS256) //用key和HS256签名
                .compact(); //最终生成
    }
    // 解析token
    public Long parseUserId(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key) // 校验前面
                .build()
                .parseClaimsJws(token)// 解析token
                .getBody();
        return Long.valueOf(claims.getSubject()); //取出 userId
    }
}

