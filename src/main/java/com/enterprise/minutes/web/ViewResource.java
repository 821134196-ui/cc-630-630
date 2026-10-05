package com.enterprise.minutes.web;

import com.enterprise.minutes.domain.*;
import com.enterprise.minutes.security.CurrentUser;
import com.enterprise.minutes.service.AccessService;
import com.enterprise.minutes.service.FileStorageService;
import com.enterprise.minutes.security.LinkTokens;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.nio.file.Files;
import java.util.Map;

/**
 * 员工访问入口：打开纪要链接、下载附件。
 *
 * 两个动作都【逐次】实时核验，流程刻意拆成三步：
 *   1) 短事务内计算核验结论（AccessService）并提交；
 *   2) 用独立事务写审计（拒绝也一定落库，不随 403 回滚）；
 *   3) 再返回内容或抛出 403。
 * 物理文件不通过静态目录暴露，无法绕过此处直接读取。
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
public class ViewResource {

    /** 令牌不设短有效期：资格由实时授权/岗位决定，而不是由令牌年龄决定。 */
    private static final long TOKEN_MAX_AGE = 60L * 60 * 24 * 365 * 10;

    @Inject
    CurrentUser currentUser;

    @Inject
    AccessService accessService;

    @Inject
    FileStorageService storage;

    @Inject
    LinkTokens tokens;

    @Inject
    com.enterprise.minutes.service.AuditService auditService;

    public record AttachmentView(Long id, String filename, String contentType, long size, int versionNo) {
    }

    public record BodyView(Long minutesId, String title, String classification, String classificationLabel,
                           int versionNo, String body, java.time.LocalDateTime publishedAt,
                           java.util.List<AttachmentView> attachments, String accessStatus) {
    }

    /** 打开提醒链接：每次都实时核验。 */
    @GET
    @Path("/view/{id}")
    public BodyView view(@PathParam("id") Long id, @QueryParam("t") String token) {
        Employee me = currentUser.require();
        long[] parsed = tokens.parse(token, TOKEN_MAX_AGE);
        if (parsed == null || parsed[0] != id) {
            // 令牌解析失败：minutes 可能存在，尝试带上 id 记录拒绝
            auditService.log(me, id, null, AuditAction.VIEW_DENIED, false, "链接令牌无效或已损坏", token);
            throw new ForbiddenException("链接令牌无效或已损坏");
        }
        var result = accessService.evaluateView(me, id, parsed[1]);
        if (!result.decision().allowed()) {
            auditService.log(me, id, null, AuditAction.VIEW_DENIED, false,
                    result.decision().reason(), token);
            throw new ForbiddenException(result.decision().reason());
        }
        var data = accessService.loadBodyData(id);
        if (data == null) {
            auditService.log(me, id, null, AuditAction.VIEW_DENIED, false, "纪要不存在或尚未发布", token);
            throw new NotFoundException("纪要不存在或尚未发布");
        }
        auditService.log(me, id, null, AuditAction.VIEW_BODY, true,
                "查看 v" + data.versionNo() + " 正文", token);
        var atts = data.attachments().stream()
                .map(a -> new AttachmentView(a.id(), a.filename(), a.contentType(), a.size(), a.versionNo()))
                .toList();
        return new BodyView(data.minutesId(), data.title(), data.classification(),
                data.classificationLabel(), data.versionNo(), data.body(), data.publishedAt(),
                atts, "ALLOWED");
    }

    /** 附件下载：与正文相同的实时核验，且附件必须属于当前发布版本。 */
    @GET
    @Path("/attachments/{attachmentId}/download")
    public Response download(@PathParam("attachmentId") Long attachmentId,
                             @QueryParam("t") String token) {
        Employee me = currentUser.require();
        long[] parsed = tokens.parse(token, TOKEN_MAX_AGE);
        if (parsed == null) {
            auditService.log(me, null, attachmentId, AuditAction.DOWNLOAD_DENIED, false,
                    "链接令牌无效或已损坏", token);
            throw new ForbiddenException("链接令牌无效或已损坏");
        }
        var result = accessService.evaluateDownload(me, attachmentId, parsed[1]);
        Long minutesId = result.minutesId() != null ? result.minutesId()
                : (parsed.length > 0 ? parsed[0] : null);
        if (minutesId != null && parsed[0] != minutesId) {
            auditService.log(me, minutesId, attachmentId, AuditAction.DOWNLOAD_DENIED, false,
                    "令牌与附件所属纪要不匹配", token);
            throw new ForbiddenException("链接令牌无效或已损坏");
        }
        if (!result.decision().allowed()) {
            auditService.log(me, minutesId, attachmentId, AuditAction.DOWNLOAD_DENIED, false,
                    result.decision().reason(), token);
            throw new ForbiddenException(result.decision().reason());
        }
        auditService.log(me, minutesId, attachmentId, AuditAction.DOWNLOAD_ATTACHMENT, true,
                "下载附件 " + result.filename(), token);

        java.nio.file.Path file = storage.resolve(result.storagePath());
        if (!Files.exists(file)) {
            throw new NotFoundException("附件文件缺失");
        }
        byte[] content;
        try {
            content = Files.readAllBytes(file);
        } catch (Exception e) {
            throw new WebApplicationException("读取附件失败", 500);
        }
        String disposition = "attachment; filename*=UTF-8''"
                + java.net.URLEncoder.encode(result.filename(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        return Response.ok(content)
                .type(result.contentType() != null ? result.contentType() : MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", disposition)
                .header("X-Content-Type-Options", "nosniff")
                .build();
    }

    /** 为前端构造当前员工对某纪要的访问链接（用于"我的链接"展示）。 */
    @GET
    @Path("/view/{id}/link")
    @jakarta.transaction.Transactional
    public Map<String, String> myLink(@PathParam("id") Long id) {
        Employee me = currentUser.require();
        Minutes m = Minutes.findById(id);
        if (m == null) {
            throw new NotFoundException("纪要不存在");
        }
        Grant g = Grant.findActive(m, me);
        if (g == null) {
            throw new ForbiddenException("您当前不在授权名单中，无可用链接");
        }
        String token = tokens.issue(m.id, me.id, System.currentTimeMillis() / 1000);
        return Map.of("url", "/#/view/" + m.id + "?t=" + token, "token", token);
    }
}
