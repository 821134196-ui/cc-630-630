package com.secmin.web;

import com.secmin.domain.*;
import com.secmin.service.AccessControlService;
import com.secmin.service.FileStorageService;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 员工侧唯一访问入口。链接形如 /#/view/{token}，前端用 token 调本资源。
 *
 * 安全要点：
 *  - 打开正文与下载附件都必须经过 {@link AccessControlService} 实时核验；
 *  - 附件不提供任何静态资源路径，无法绕过正文页面直接读文件；
 *  - 下载同样校验 token 与登录人，且附件必须属于授权版本。
 */
@Path("/api/access")
@Produces(MediaType.APPLICATION_JSON)
public class AccessResource {

    @Inject
    AccessControlService accessControl;

    @Inject
    FileStorageService fileStorage;

    @Inject
    EntityManager em;

    @Inject
    CurrentUser current;

    /**
     * 打开链接：每次都按当前岗位、密级、授权与版本状态实时核验。
     * 核验通过才返回正文与附件清单；任何一项不满足返回 403 及原因（审计已记录）。
     */
    @GET
    @Path("/view/{token}")
    public Response view(@PathParam("token") String token) {
        AppUser me = current.require();
        AccessControlService.AccessContext ctx =
                accessControl.authorize(token, me, AuditAction.OPEN_LINK, null);

        List<Attachment> atts = em.createQuery(
                        "select a from Attachment a where a.versionId = :v order by a.id",
                        Attachment.class)
                .setParameter("v", ctx.version.getId()).getResultList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("minutesId", ctx.minutes.getId());
        result.put("minutesTitle", ctx.minutes.getTitle());
        result.put("versionNo", ctx.version.getVersionNo());
        result.put("classification", ctx.version.getClassification().name());
        result.put("classificationLabel", ctx.version.getClassification().getLabel());
        result.put("classificationLevel", ctx.version.getClassification().getLevel());
        result.put("bodyText", ctx.version.getBodyText());
        result.put("changeNote", ctx.version.getChangeNote());
        result.put("reviewedAt", ctx.version.getReviewedAt() == null ? null
                : ctx.version.getReviewedAt().toString());
        result.put("viewer", UserResource.userDto(ctx.user));
        result.put("token", ctx.grant.getToken());
        result.put("attachments", atts.stream().map(a -> {
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("id", a.getId());
            dto.put("fileName", a.getFileName());
            dto.put("contentType", a.getContentType());
            dto.put("sizeBytes", a.getSizeBytes());
            dto.put("downloadUrl",
                    "/api/access/attachment/" + ctx.grant.getToken() + "/" + a.getId());
            return dto;
        }).toList());
        return Response.ok(result).build();
    }

    /**
     * 当前员工收到的全部定向分发链接（历史记录保留），并给出此刻是否仍可访问的实时状态。
     * 员工据此打开自己的专属链接。
     */
    @GET
    @Path("/my-links")
    public List<Map<String, Object>> myLinks() {
        AppUser me = current.require();
        List<DistributionGrant> grants = em.createQuery(
                        "select g from DistributionGrant g where g.userId = :u order by g.id desc",
                        DistributionGrant.class)
                .setParameter("u", me.getId()).getResultList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (DistributionGrant g : grants) {
            Minutes m = em.find(Minutes.class, g.getMinutesId());
            MinutesVersion v = em.find(MinutesVersion.class, g.getVersionId());
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("grantId", g.getId());
            dto.put("token", g.getToken());
            dto.put("minutesId", g.getMinutesId());
            dto.put("minutesTitle", m == null ? null : m.getTitle());
            dto.put("versionNo", v == null ? null : v.getVersionNo());
            dto.put("classification", v == null ? null : v.getClassification().name());
            dto.put("classificationLabel", v == null ? null : v.getClassification().getLabel());
            dto.put("state", v == null ? null : v.getState().name());
            dto.put("stateLabel", v == null ? null : v.getState().getLabel());
            dto.put("revoked", g.isRevoked());
            dto.put("createdAt", g.getCreatedAt().toString());
            dto.put("lastAccessAt", g.getLastAccessAt() == null ? null
                    : g.getLastAccessAt().toString());
            boolean accessible = !g.isRevoked() && v != null
                    && v.getState() == VersionState.APPROVED
                    && me.getClearanceLevel() >= v.getClassification().getLevel();
            dto.put("accessibleNow", accessible);
            dto.put("inaccessibleReason", accessible ? null : inaccessibleReason(g, v, me));
            out.add(dto);
        }
        return out;
    }

    private String inaccessibleReason(DistributionGrant g, MinutesVersion v, AppUser me) {
        if (g.isRevoked()) {
            return "定向授权已被撤销";
        }
        if (v == null) {
            return "版本不存在";
        }
        return switch (v.getState()) {
            case WITHDRAWN -> "负责人已撤回审批";
            case SUPERSEDED -> "该版本已被新版替代";
            case PENDING -> "版本尚未审批通过";
            case APPROVED -> me.getClearanceLevel() < v.getClassification().getLevel()
                    ? "转岗后岗位密级不足" : null;
        };
    }

    /**
     * 附件下载：全程权限校验。Content-Disposition attachment 强制下载，不内联渲染。
     * 任何未授权请求返回 403，且已写入审计。
     */
    @GET
    @Path("/attachment/{token}/{attachmentId}")
    public Response download(@PathParam("token") String token,
                             @PathParam("attachmentId") Long attachmentId) {
        AppUser me = current.require();
        Attachment att = accessControl.authorizeAttachment(token, me, attachmentId);
        byte[] bytes = fileStorage.read(att.getStoragePath());

        String encodedName = java.net.URLEncoder.encode(att.getFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return Response.ok(bytes, att.getContentType())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedName)
                .header("X-Content-Type-Options", "nosniff")
                .header("Cache-Control", "no-store")
                .build();
    }
}
