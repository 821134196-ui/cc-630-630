package com.enterprise.minutes.web;

import com.enterprise.minutes.domain.Employee;
import com.enterprise.minutes.domain.Role;
import com.enterprise.minutes.security.CurrentUser;
import com.enterprise.minutes.service.MinutesService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;

/** 员工目录与人事调动（管理员）。 */
@Path("/api/employees")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EmployeeResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    MinutesService minutesService;

    public record EmployeeDto(Long id, String username, String name, String email, String role,
                              String department, String position, int clearanceLevel, boolean active) {
        static EmployeeDto of(Employee e) {
            return new EmployeeDto(e.id, e.username, e.name, e.email, e.role.name(),
                    e.department, e.position, e.clearanceLevel, e.active);
        }
    }

    @GET
    public List<EmployeeDto> list() {
        Employee me = currentUser.require();
        // 员工目录仅对记录员/负责人/管理员开放（选择接收人、审批、人事调动需要）
        if (me.role == Role.STAFF) {
            throw new ForbiddenException("无权查看员工目录");
        }
        return Employee.<Employee>listAll().stream().map(EmployeeDto::of).toList();
    }

    public record CreateEmployeeRequest(String username, String name, String email, String role,
                                        String department, String position, Integer clearanceLevel) {
    }

    @POST
    @Transactional
    public EmployeeDto create(CreateEmployeeRequest req) {
        currentUser.requireRole(Role.ADMIN);
        if (req.username() == null || req.username().isBlank() || req.name() == null) {
            throw new BadRequestException("用户名/姓名不能为空");
        }
        if (Employee.findByUsername(req.username()) != null) {
            throw new BadRequestException("用户名已存在");
        }
        Employee e = new Employee();
        e.username = req.username().trim();
        e.name = req.name();
        e.email = req.email() != null ? req.email() : req.username().trim() + "@example.com";
        e.role = Role.valueOf(req.role() == null ? "STAFF" : req.role());
        e.department = req.department() != null ? req.department() : "未分配";
        e.position = req.position() != null ? req.position() : "员工";
        e.clearanceLevel = req.clearanceLevel() != null
                ? Math.max(1, Math.min(4, req.clearanceLevel())) : 1;
        e.persist();
        return EmployeeDto.of(e);
    }

    /** 转岗/调岗/资质变更：旧岗位上的授权立即失效。 */
    public record TransferRequest(String department, String position, Integer clearanceLevel) {
    }

    @PUT
    @Path("/{username}/transfer")
    public Map<String, String> transfer(@PathParam("username") String username, TransferRequest req) {
        Employee admin = currentUser.requireRole(Role.ADMIN);
        if (req == null || req.department() == null || req.position() == null
                || req.clearanceLevel() == null) {
            throw new BadRequestException("部门、岗位、密级资质均不能为空");
        }
        minutesService.transfer(admin, username, req.department(), req.position(),
                Math.max(1, Math.min(4, req.clearanceLevel())));
        return Map.of("status", "ok", "username", username);
    }
}
