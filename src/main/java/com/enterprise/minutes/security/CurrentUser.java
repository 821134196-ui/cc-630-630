package com.enterprise.minutes.security;

import com.enterprise.minutes.domain.Employee;
import com.enterprise.minutes.domain.Role;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;

/**
 * 当前请求的登录员工。
 *
 * 直接从 Vert.x RoutingContext 读取 X-Username 头（演示认证）并懒加载员工实体，
 * 避免依赖"请求过滤器向请求作用域 Bean 传值"——在 Quarkus REST 中过滤器与资源
 * 不一定共享同一个请求作用域实例。所有权限判断都在服务端基于当前请求实时进行。
 */
@RequestScoped
public class CurrentUser {

    @Inject
    RoutingContext routingContext;

    String username;
    Employee employee;
    boolean resolved;

    private void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        String header = routingContext.request().getHeader("X-Username");
        if (header != null && !header.isBlank()) {
            username = header.trim();
            employee = Employee.findByUsername(username);
        }
    }

    public Employee require() {
        resolve();
        if (employee == null) {
            throw new NotAuthorizedException("未登录或用户不存在");
        }
        if (!employee.active) {
            throw new ForbiddenException("账号已停用");
        }
        return employee;
    }

    public Employee requireRole(Role role) {
        Employee e = require();
        if (e.role != role) {
            throw new ForbiddenException("需要角色: " + role);
        }
        return e;
    }

    public Employee orNull() {
        resolve();
        return employee;
    }
}
