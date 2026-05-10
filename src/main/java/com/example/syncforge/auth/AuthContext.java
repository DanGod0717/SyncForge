package com.example.syncforge.auth;

public final class AuthContext {
    //存储当前线程的用户信息（主要是用户ID）
    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<Long>();

    private AuthContext() {
    }

    public static void setUserId(Long userId) {
        USER_ID_HOLDER.set(userId);
    }

    public static Long getUserId() {
        return USER_ID_HOLDER.get();
    }

    public static void clear() {
        USER_ID_HOLDER.remove();
    }
}

