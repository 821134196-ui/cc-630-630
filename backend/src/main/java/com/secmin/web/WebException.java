package com.secmin.web;

/**
 * 业务侧主动抛出的 HTTP 异常（如 403/400/404），由 {@link ApiExceptionMapper} 统一转 JSON。
 */
public class WebException extends RuntimeException {

    private final int status;

    public WebException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
