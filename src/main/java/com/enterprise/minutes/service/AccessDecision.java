package com.enterprise.minutes.service;

/** 实时权限核验结果。每次请求都重新计算，绝不缓存。 */
public record AccessDecision(boolean allowed, String reason) {

    public static AccessDecision ok() {
        return new AccessDecision(true, null);
    }

    public static AccessDecision deny(String reason) {
        return new AccessDecision(false, reason);
    }
}
