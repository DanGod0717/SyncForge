package com.example.syncforge.realtime;

import com.example.syncforge.auth.JwtUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {
// WebSocket握手阶段的JWT鉴权 握手拦截器 在webSocket连接建立前后
    // 定义一个固定key，把登录id存入会话属性，后面的CONNECT/SUBSCRIBE可以读出来
    public static final String ATTR_USER_ID = "wsUserId";
// 注入jwtUtil
    private final JwtUtil jwtUtil;

    public JwtHandshakeInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        //先从请求中获得token
        String token = extractToken(request);
        // 如果token为空，握手失败
        if (!StringUtils.hasText(token)) {
            return false;
        }
        try {
            //成功拿到 写入属性
            Long userId = jwtUtil.parseUserId(token);
            attributes.put(ATTR_USER_ID, userId);
            // return true 放行 false不放行
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
    //握手后的处理
    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
    }
    // 提取token 支持两种来源
    private String extractToken(ServerHttpRequest request) {
        // 从 websocke 握手请求里拿到 jwt字符串，优先从query参数拿，否则再去Authorization 头拿

        // request.getURI() 拿当前请求的URI()例如：ws://localhost:8080/ws?token=abc123
        //UriComponentsBuilder.fromUri(...).build()：把 URI 解析成结构化对象 把uri解析成结构化对象
        // getQueryParams().getFirst("token")：取 query 参数里 key=token 的第一个值
        //结果：如果 URL 带了 ?token=xxx，这里就拿到 xxx
        String tokenFromQuery = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("token");
        // hasText 比!=nULL更严格，要求不是null不是空字符串，不是全空格。只要有合法token返回
        if (StringUtils.hasText(tokenFromQuery)) {
            return tokenFromQuery;
        }
        // 从握手请求投取token 期望格式：Authorization: Bearer <token>
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        //如果上一步没拿到（空/null），并且当前请求是 servlet 类型，就从底层 HttpServletRequest 再读一次，做兼容兜底。
        if (!StringUtils.hasText(authorization) && request instanceof ServletServerHttpRequest) {
            authorization = ((ServletServerHttpRequest) request).getServletRequest().getHeader(HttpHeaders.AUTHORIZATION);
        }
        // 去除前面字符
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }
}

