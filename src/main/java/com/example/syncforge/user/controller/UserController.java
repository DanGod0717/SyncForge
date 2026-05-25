package com.example.syncforge.user.controller;


import com.example.syncforge.common.ApiResponse;
import com.example.syncforge.auth.JwtUtil;
import com.example.syncforge.user.dto.LoginRequest;
import com.example.syncforge.user.dto.RefreshTokenRequest;
import com.example.syncforge.user.dto.UserResponse;
import com.example.syncforge.user.entity.User;
import com.example.syncforge.user.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;
    private final JwtUtil jwtUtil;

    public UserController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/{id}")
    public ApiResponse<UserResponse> getByID(@PathVariable("id") Long id){
        User user = userService.getById(id);
        if (user == null) {
            return ApiResponse.error(404, "User not found");
        }
        return ApiResponse.success(UserResponse.from(user));
    }

    @GetMapping("/by-username/{username}")
    public ApiResponse<UserResponse> getByUsername(@PathVariable("username") String username){
        User user = userService.getByUsername(username);
        if (user == null) {
            return ApiResponse.error(404, "User not found");
        }
        return ApiResponse.success(UserResponse.from(user));
    }

    // 创建用户 register
    @PostMapping
    public ApiResponse<UserResponse> create(@RequestBody User user) {
        String resolvedPassword = user == null ? null : resolvePassword(user);
        if (user == null || user.getUsername() == null || user.getEmail() == null
                || resolvedPassword == null || resolvedPassword.trim().isEmpty()) {
            return ApiResponse.error(400, "username, email and password are required");
        }
        user.setPasswordHash(resolvedPassword);
        if (user.getStatus() == null) {
            user.setStatus(1);
        }
        int affected = userService.create(user);
        if (affected <= 0) {
            return ApiResponse.error(500, "Create user failed");
        }
        return ApiResponse.success(UserResponse.from(user));
    }

    // 登录
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody LoginRequest request) {
        String resolvedPassword = request == null ? null : request.getResolvedPassword();
        if (request == null || request.getUsername() == null || resolvedPassword == null) {
            return ApiResponse.error(400, "username and password are required");
        }

        User user = userService.login(request.getUsername(), resolvedPassword);
        if (user == null) {
            return ApiResponse.error(401, "Invalid username or password");
        }

        String accessToken = jwtUtil.generateToken(user);
        String refreshToken = userService.issueRefreshToken(user);

        Map<String, Object> payload = new HashMap<>();
        // 兼容旧前端：token 字段仍返回 access token
        payload.put("token", accessToken);
        payload.put("accessToken", accessToken);
        payload.put("refreshToken", refreshToken);
        payload.put("userId", user.getId());
        payload.put("username", user.getUsername());
        return ApiResponse.success(payload);
    }

    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refresh(@RequestBody RefreshTokenRequest request) {
        String refreshToken = request == null ? null : request.getRefreshToken();
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            return ApiResponse.error(400, "refreshToken is required");
        }

        try {
            String accessToken = userService.refreshAccessToken(refreshToken);
            Map<String, Object> payload = new HashMap<>();
            payload.put("token", accessToken);
            payload.put("accessToken", accessToken);
            return ApiResponse.success(payload);
        } catch (IllegalArgumentException ex) {
            return ApiResponse.error(401, ex.getMessage());
        }
    }

    @PostMapping("/quit")
    public ApiResponse<Map<String, Object>> quit(@RequestBody(required = false) RefreshTokenRequest request) {
        try {
            if (request != null && request.getRefreshToken() != null && !request.getRefreshToken().trim().isEmpty()) {
                userService.revokeRefreshToken(request.getRefreshToken());
            }
        } catch (Exception ignored) {
            // Logout should be idempotent. Client still needs to clear local tokens.
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("message", "Logged out");
        payload.put("action", "Please remove accessToken/refreshToken on client side");
        return ApiResponse.success(payload);
    }

    private String resolvePassword(User user) {
        if (user.getPassword() != null && !user.getPassword().trim().isEmpty()) {
            return user.getPassword();
        }
        return user.getPasswordHash();
    }
}
