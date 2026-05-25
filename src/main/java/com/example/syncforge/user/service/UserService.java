package com.example.syncforge.user.service;

import com.example.syncforge.auth.JwtUtil;
import com.example.syncforge.user.entity.RefreshToken;
import com.example.syncforge.user.entity.User;
import com.example.syncforge.user.mapper.RefreshTokenMapper;
import com.example.syncforge.user.mapper.UserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
public class UserService {

    private final UserMapper userMapper;
    // 编码器hash
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RefreshTokenMapper refreshTokenMapper;

    public UserService(UserMapper userMapper,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       RefreshTokenMapper refreshTokenMapper) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.refreshTokenMapper = refreshTokenMapper;
    }

    public User getById(Long id){
        return userMapper.findById(id);
    }

    public User getByUsername(String username){
        return userMapper.findByUsername(username);
    }

    // 使用用户名查找后再进行 BCrypt 校验
    public User login(String username, String rawPassword) {
        // 找用户
        User user = userMapper.findByUsername(username);
        // 没该用户或者前端没传密码
        if (user == null || rawPassword == null) {
            return null;
        }
        // matches 内部完成hash比较 如果匹配成功返回用户对象 否则返回null
        return passwordEncoder.matches(rawPassword, user.getPasswordHash()) ? user : null;
    }

    public int create(User user){
        if (user.getPasswordHash() != null && !user.getPasswordHash().trim().isEmpty()) {
            // 编码后加入 user 库中存的是密文
            user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
        }
        return userMapper.insert(user);
    }

    public String issueRefreshToken(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("User is required for issuing refresh token");
        }

        String tokenJti = UUID.randomUUID().toString();
        String refreshToken = jwtUtil.generateRefreshToken(user, tokenJti);
        JwtUtil.RefreshTokenClaims claims = jwtUtil.parseRefreshToken(refreshToken);

        RefreshToken entity = new RefreshToken();
        entity.setUserId(user.getId());
        entity.setTokenJti(tokenJti);
        entity.setTokenHash(sha256(refreshToken));
        entity.setExpiresAt(LocalDateTime.ofInstant(claims.getExpiration().toInstant(), ZoneId.systemDefault()));

        int affected = refreshTokenMapper.insert(entity);
        if (affected <= 0) {
            throw new IllegalStateException("Issue refresh token failed");
        }
        return refreshToken;
    }

    public String refreshAccessToken(String refreshToken) {
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            throw new IllegalArgumentException("refreshToken is required");
        }

        JwtUtil.RefreshTokenClaims claims = jwtUtil.parseRefreshToken(refreshToken);
        RefreshToken stored = refreshTokenMapper.findByTokenJti(claims.getTokenJti());
        if (stored == null || stored.getRevokedAt() != null) {
            throw new IllegalArgumentException("Invalid refresh token");
        }
        if (!sha256(refreshToken).equals(stored.getTokenHash())) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        LocalDateTime now = LocalDateTime.now();
        if (stored.getExpiresAt() != null && stored.getExpiresAt().isBefore(now)) {
            throw new IllegalArgumentException("Refresh token expired");
        }

        User user = userMapper.findById(claims.getUserId());
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        return jwtUtil.generateAccessToken(user);
    }

    public void revokeRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            return;
        }

        JwtUtil.RefreshTokenClaims claims = jwtUtil.parseRefreshToken(refreshToken);
        RefreshToken stored = refreshTokenMapper.findByTokenJti(claims.getTokenJti());
        if (stored == null || stored.getRevokedAt() != null) {
            return;
        }
        if (!sha256(refreshToken).equals(stored.getTokenHash())) {
            return;
        }
        refreshTokenMapper.revokeByTokenJti(claims.getTokenJti(), LocalDateTime.now());
    }

    private String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
