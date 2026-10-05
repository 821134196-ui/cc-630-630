package com.secmin.web;

import com.secmin.domain.*;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;

@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
public class UserResource {

    @Inject
    EntityManager em;

    @Inject
    CurrentUser current;

    @Inject
    com.secmin.service.MinutesService minutesService;

    @GET
    @Path("/me")
    public Map<String, Object> me() {
        AppUser u = current.require();
        return userDto(u);
    }

    /** 全员名册（选择接收人、管理员操作用）。 */
    @GET
    public List<Map<String, Object>> list() {
        current.require();
        return em.createQuery("select u from AppUser u order by u.id", AppUser.class)
                .getResultList().stream().map(UserResource::userDto).toList();
    }

    /** 管理员：员工转岗（新岗位 + 新密级，立即影响全部旧链接的实时核验）。 */
    @POST
    @Path("/{id}/transfer")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Map<String, Object> transfer(@PathParam("id") Long id, Map<String, Object> body) {
        AppUser admin = current.require();
        if (admin.getRole() != Role.ADMIN) {
            throw new WebException(403, "仅管理员可调整员工岗位");
        }
        String post = String.valueOf(body.getOrDefault("post", "")).trim();
        int level = Integer.parseInt(String.valueOf(body.get("clearanceLevel")));
        if (post.isEmpty()) {
            throw new WebException(400, "新岗位不能为空");
        }
        AppUser u = minutesService.transferPost(id, post, level, admin.getId());
        return userDto(u);
    }

    static Map<String, Object> userDto(AppUser u) {
        Map<String, Object> dto = new java.util.LinkedHashMap<>();
        dto.put("id", u.getId());
        dto.put("account", u.getAccount());
        dto.put("name", u.getName());
        dto.put("email", u.getEmail());
        dto.put("role", u.getRole().name());
        dto.put("roleLabel", u.getRole().getLabel());
        dto.put("post", u.getCurrentPost());
        dto.put("clearanceLevel", u.getClearanceLevel());
        dto.put("clearanceLabel", Classification.ofLevel(u.getClearanceLevel()).getLabel());
        dto.put("enabled", u.isEnabled());
        dto.put("postUpdatedAt", u.getPostUpdatedAt() == null ? "" : u.getPostUpdatedAt().toString());
        return dto;
    }
}
