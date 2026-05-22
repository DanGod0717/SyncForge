package com.example.syncforge.auth;

import com.example.syncforge.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class AuthInterceptor implements HandlerInterceptor {
//HandlerInterceptor 是 Spring 提供的接口，用于拦截 HTTP 请求
//拦截器可以在请求到 Controller 前、后做处理
//这里的 AuthInterceptor 作用是 验证用户是否已登录（JWT），并把用户ID放进 AuthContext

    private final JwtUtil jwtUtil;
    //ObjectMapper 是 Jackson 库的核心类，用于将 Java 对象转换为 JSON 字符串。
    private final ObjectMapper objectMapper;

    public AuthInterceptor(JwtUtil jwtUtil, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }
    // 请求前处理
    //Controller 执行前调用
    //返回 true → 放行请求
    //返回 false → 拦截请求，不继续执行 Controller
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String authHeader = request.getHeader("Authorization");
        //
        //取 HTTP 请求头 Authorization
        //JWT 标准：Authorization: Bearer <token>
        //如果没有或不合法 → 返回 401（未授权）
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeUnauthorized(response, "Missing or invalid Authorization header");
            return false;
        }

        String token = authHeader.substring(7);
        //去掉 "Bearer " 前缀，拿到纯 token
        //jwtUtil.parseUserId(token) → 验证 token，有效就解析出用户ID
        //放入 ThreadLocal 上下文 AuthContext → 这样后续 Controller/Service 可以直接 AuthContext.getUserId()
        //返回 true → 允许请求继续执行
        try {
            Long userId = jwtUtil.parseUserId(token);
            AuthContext.setUserId(userId);
            return true;
        } catch (Exception ex) {
            writeUnauthorized(response, "Invalid or expired token");
            return false;
        }
    }
    //请求处理完成后（不管成功或异常）都会调用
    //清理 ThreadLocal → 防止内存泄漏（尤其是线程池环境）
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }
    //返回统一格式的错误信息：
    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(401, message)));
    }
}

