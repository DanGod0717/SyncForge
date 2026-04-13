package com.example.syncforge.user.controller;


import com.example.syncforge.common.ApiResponse;
import com.example.syncforge.auth.JwtUtil;
import com.example.syncforge.user.dto.LoginRequest;
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
        // 格式化返回 user
        return ApiResponse.success(UserResponse.from(user));
    }

    @GetMapping("/by-username/{username}")
    public ApiResponse<UserResponse> getByUsername(@PathVariable("username") String username){
        User user = userService.getByUsername(username);
        if (user == null) {
            return ApiResponse.error(404, "User not found");
        }
        // 格式化返回 user
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
        // 注册成功返回
        return ApiResponse.success(UserResponse.from(user));
    }
    // 登录
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody LoginRequest request) {
        String resolvedPassword = request == null ? null : request.getResolvedPassword();
        if (request == null || request.getUsername() == null || resolvedPassword == null) {
            return ApiResponse.error(400, "username and password are required");
        }
        // 先获得请求的姓名和密码 进行登录
        User user = userService.login(request.getUsername(), resolvedPassword);
        // 如果没有则失败
        if (user == null) {
            return ApiResponse.error(401, "Invalid username or password");
        }
        // 将user转为token
        String token = jwtUtil.generateToken(user);
        // 放主要信息
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("token", token);
        payload.put("userId", user.getId());
        payload.put("username", user.getUsername());
        return ApiResponse.success(payload);
    }

    private String resolvePassword(User user) {
        //使用password 明文还是加密后的
        if (user.getPassword() != null && !user.getPassword().trim().isEmpty()) {
            return user.getPassword();
        }
        return user.getPasswordHash();
    }
}
