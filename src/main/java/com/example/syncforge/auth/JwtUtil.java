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
        //配置文件中的字符串密钥 jwtSecret 转换为 JWT 签名/验签所需要的 SecretKey 对象。
        byte[] bytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        // 转为适用于hmac算法的 secretKey
        this.key = Keys.hmacShaKeyFor(
                bytes.length>=32? bytes : (jwtSecret+"-padding-to-32-bytes").getBytes(StandardCharsets.UTF_8)
        );
    }
    // 产生token
    public String generateToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expireMs);

        String token = Jwts.builder()
                .setSubject(String.valueOf(user.getId())) // userId 作为 subject
                .claim("username", user.getUsername()) // 可选：把用户名也放进 token 里
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
        return token;
    }
    // 解析token
    public Long parseUserId(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token).getBody();

        return Long.valueOf(claims.getSubject()); //取出 userId
    }

    public String parseUsername(String token){
        Claims claims= Jwts.parserBuilder()
                .setSigningKey(key).build()
                .parseClaimsJws(token).getBody();
        return String.valueOf(claims.get("username", String.class));
    }
}

