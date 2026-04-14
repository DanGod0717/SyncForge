package com.example.syncforge.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
// 标记这是 WebSocket 配置类
// 实现 WebSocketMessageBrokerConfigurer 用于配置 STOMP WebSocket
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // JWT 握手拦截器：在“建立 WebSocket 连接之前”做登录校验
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    // STOMP 消息通道拦截器：在“消息发送/接收时”做权限控制
    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    // 构造器注入两个拦截器（Spring 自动注入）
    public WebSocketConfig(JwtHandshakeInterceptor jwtHandshakeInterceptor,
                           StompAuthChannelInterceptor stompAuthChannelInterceptor) {
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
        this.stompAuthChannelInterceptor = stompAuthChannelInterceptor;
    }


    // 1️⃣ 注册 WebSocket 连接端点
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {

        // 注册一个 WebSocket 连接地址：/ws
        registry.addEndpoint("/ws")

                // 允许跨域连接（所有域都允许）
                .setAllowedOriginPatterns("*")

                // 在“握手阶段”拦截请求（校验 JWT / 用户身份）
                .addInterceptors(jwtHandshakeInterceptor);

        // 第二种连接方式：SockJS（兼容不支持 WebSocket 的浏览器）
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(jwtHandshakeInterceptor)
                .withSockJS(); // 自动降级（长轮询等）
    }


    // 2️⃣ 配置消息路由规则
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {

        // 客户端发送消息前缀
        // 例如：/app/document/edit
        // 会路由到 @MessageMapping 方法
        registry.setApplicationDestinationPrefixes("/app");

        // 启用内置消息 broker（简单版消息队列）
        // 支持订阅：
        // /topic → 广播消息（群发）
        // /queue → 点对点消息
        registry.enableSimpleBroker("/topic", "/queue");

        // 用户私有消息前缀
        // 例如：/user/queue/xxx
        // 用于一对一推送
        registry.setUserDestinationPrefix("/user");
    }

    // 3️⃣ 拦截 STOMP 消息（核心安全层）
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {

        // 注册拦截器：拦截所有客户端发来的 STOMP 消息
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
