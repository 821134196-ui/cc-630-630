package com.secmin.web;

import com.secmin.service.AccessDeniedException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<Throwable> {

    @Override
    public Response toResponse(Throwable e) {
        int status;
        String code;
        if (e instanceof AccessDeniedException ad) {
            status = 403;
            code = "ACCESS_DENIED";
            return Response.status(status)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(Map.of(
                            "error", code,
                            "message", ad.getMessage(),
                            "denied", true))
                    .build();
        } else if (e instanceof WebException w) {
            status = w.getStatus();
            code = "ERROR";
        } else if (e instanceof WebApplicationException wae
                && wae.getResponse() != null) {
            // 保留框架状态码（如无对应路由的 404），不泄漏内部信息
            status = wae.getResponse().getStatus();
            code = status == 404 ? "NOT_FOUND" : "HTTP_ERROR";
            String msg = status == 404 ? "资源不存在" : String.valueOf(e.getMessage());
            return Response.status(status)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(Map.of("error", code, "message", msg))
                    .build();
        } else if (e instanceof IllegalArgumentException) {
            status = 400;
            code = "BAD_REQUEST";
        } else if (e instanceof IllegalStateException) {
            status = 409;
            code = "CONFLICT";
        } else {
            status = 500;
            code = "INTERNAL";
        }
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", code, "message", String.valueOf(e.getMessage())))
                .build();
    }
}
