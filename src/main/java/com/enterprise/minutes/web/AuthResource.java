package com.enterprise.minutes.web;

import com.enterprise.minutes.domain.*;
import com.enterprise.minutes.security.CurrentUser;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/** 登录（演示）与当前会话。 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    CurrentUser currentUser;

    public record LoginRequest(String username) {
    }

    public record EmployeeDto(Long id, String username, String name, String email, String role,
                              String department, String position, int clearanceLevel, boolean active) {
        static EmployeeDto of(Employee e) {
            return new EmployeeDto(e.id, e.username, e.name, e.email, e.role.name(),
                    e.department, e.position, e.clearanceLevel, e.active);
        }
    }

    @POST
    @Path("/login")
    public EmployeeDto login(LoginRequest req) {
        if (req == null || req.username() == null || req.username().isBlank()) {
            throw new BadRequestException("用户名不能为空");
        }
        Employee e = Employee.findByUsername(req.username().trim());
        if (e == null) {
            throw new NotFoundException("用户不存在: " + req.username());
        }
        return EmployeeDto.of(e);
    }

    @GET
    @Path("/me")
    public EmployeeDto me() {
        return EmployeeDto.of(currentUser.require());
    }
}
