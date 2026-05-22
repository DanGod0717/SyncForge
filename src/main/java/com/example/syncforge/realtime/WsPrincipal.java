package com.example.syncforge.realtime;

import java.security.Principal;
//java.security.Principal 是 Java 标准库中的一个接口，用于表示一个可以认证的身份（通常指登录的用户）。在 Spring 中，它广泛用于表示当前访问系统的用户身份
public class WsPrincipal implements Principal {

    private final Long userId;

    public WsPrincipal(Long userId) {
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}

