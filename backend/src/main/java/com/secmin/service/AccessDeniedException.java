package com.secmin.service;

/**
 * 访问被拒绝时抛出，携带面向用户的中文原因，供审计与前端展示。
 */
public class AccessDeniedException extends RuntimeException {

    private final String token;
    private final Long userId;

    public AccessDeniedException(String reason, String token, Long userId) {
        super(reason);
        this.token = token;
        this.userId = userId;
    }

    public String getToken() { return token; }
    public Long getUserId() { return userId; }
}
