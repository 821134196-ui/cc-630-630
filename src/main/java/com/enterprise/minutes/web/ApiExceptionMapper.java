package com.enterprise.minutes.web;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

/** 统一错误响应，避免泄漏堆栈。 */
@Provider
public class ApiExceptionMapper implements ExceptionMapper<Exception> {

    @Override
    public Response toResponse(Exception e) {
        int status;
        if (e instanceof jakarta.ws.rs.NotAuthorizedException) {
            status = 401;
        } else if (e instanceof jakarta.ws.rs.ForbiddenException) {
            status = 403;
        } else if (e instanceof jakarta.ws.rs.NotFoundException) {
            status = 404;
        } else if (e instanceof jakarta.ws.rs.BadRequestException) {
            status = 400;
        } else {
            status = 500;
        }
        String msg = status == 500 ? "服务器内部错误" : e.getMessage();
        if (status == 500) {
            e.printStackTrace();
        }
        return Response.status(status).entity(Map.of("error", msg == null ? "" : msg)).build();
    }
}
