package com.enterprise.minutes.web;

import com.enterprise.minutes.domain.*;
import com.enterprise.minutes.security.CurrentUser;
import com.enterprise.minutes.service.MinutesService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.time.LocalDateTime;
import java.util.*;

/** 纪要起草、修订、提交、审批。 */
@Path("/api/minutes")
@Produces(MediaType.APPLICATION_JSON)
public class MinutesResource {

    @Inject
    MinutesService service;

    @Inject
    CurrentUser currentUser;

    @Inject
    com.enterprise.minutes.service.FileStorageService storage;

    /* ------------------------------- DTO ------------------------------- */

    public record AttachmentDto(Long id, String filename, String contentType, long size, int versionNo) {
    }

    public record MinutesDto(Long id, String title, String classification, String status,
                             String createdByName, String approverName,
                             Integer currentVersionNo, Integer draftVersionNo,
                             String rejectionComment, LocalDateTime updatedAt,
                             List<AttachmentDto> currentAttachments) {
    }

    public record RecipientDto(String username, String name, String department, String position,
                               int clearanceLevel, boolean active, String revokeReason) {
    }

    /** 审批预览项：候选接收人 + 此刻的实时资格判断（供负责人核对最终名单） */
    public record PreviewRow(String username, String name, String department, String position,
                             int clearanceLevel, boolean eligible, String note) {
    }

    public record ApprovalPreview(MinutesDto minutes, List<PreviewRow> candidates,
                                  List<RecipientDto> currentGrants) {
    }

    public record DraftContentDto(int versionNo, String body, boolean published,
                                  List<AttachmentDto> attachments) {
    }

    /** 审批/编辑前预览草稿版本正文与附件元数据（仅负责人或该纪要记录员）。 */
    @GET
    @Path("/{id}/draft-content")
    @Transactional
    public DraftContentDto draftContent(@PathParam("id") Long id) {
        Employee me = currentUser.require();
        Minutes m = Minutes.findById(id);
        if (m == null) {
            throw new NotFoundException("纪要不存在");
        }
        if (me.role == Role.STAFF
                || (me.role == Role.RECORDER && !m.createdBy.id.equals(me.id))) {
            throw new ForbiddenException("无权预览草稿内容");
        }
        MinutesVersion v = m.draftVersion != null ? m.draftVersion
                : (m.status == MinutesStatus.PENDING ? null : m.currentVersion);
        if (v == null) {
            throw new NotFoundException("没有可预览的草稿版本");
        }
        List<AttachmentDto> atts = v.attachments.stream()
                .map(a -> new AttachmentDto(a.id, a.filename, a.contentType, a.size, v.versionNo))
                .toList();
        return new DraftContentDto(v.versionNo, v.body, v.published, atts);
    }

    static MinutesDto toDto(Minutes m) {
        List<AttachmentDto> atts = new ArrayList<>();
        if (m.currentVersion != null) {
            for (Attachment a : m.currentVersion.attachments) {
                atts.add(new AttachmentDto(a.id, a.filename, a.contentType, a.size,
                        m.currentVersion.versionNo));
            }
        }
        return new MinutesDto(m.id, m.title, m.classification.name(), m.status.name(),
                m.createdBy.name, m.approver != null ? m.approver.name : null,
                m.currentVersion != null ? m.currentVersion.versionNo : null,
                m.draftVersion != null ? m.draftVersion.versionNo : null,
                m.rejectionComment, m.updatedAt, atts);
    }

    /* ----------------------------- 列表/详情 ----------------------------- */

    @GET
    @Transactional
    public List<MinutesDto> list() {
        Employee me = currentUser.require();
        List<Minutes> all;
        if (me.role == Role.APPROVER || me.role == Role.ADMIN) {
            all = Minutes.listAll();
        } else if (me.role == Role.RECORDER) {
            all = Minutes.list("createdBy", me);
        } else {
            all = new ArrayList<>();
        }
        // 员工：补充所有授权过的纪要（含已撤回，便于看到"已失效"状态）
        if (me.role == Role.STAFF) {
            Set<Long> seen = new HashSet<>();
            List<Minutes> granted = new ArrayList<>();
            for (Grant g : Grant.<Grant>find("employee", me).list()) {
                if (seen.add(g.minutes.id)) {
                    granted.add(g.minutes);
                }
            }
            all = granted;
        }
        return all.stream().sorted(Comparator.comparing((Minutes x) -> x.updatedAt).reversed())
                .map(MinutesResource::toDto).toList();
    }

    @GET
    @Path("/{id}")
    @Transactional
    public MinutesDto get(@PathParam("id") Long id) {
        Employee me = currentUser.require();
        Minutes m = Minutes.findById(id);
        if (m == null) {
            throw new NotFoundException("纪要不存在");
        }
        if (me.role == Role.STAFF) {
            // 员工只能看到自己授权范围内的纪要元数据
            if (Grant.find("minutes = ?1 and employee = ?2", m, me).count() == 0) {
                throw new ForbiddenException("无权查看该纪要");
            }
        } else if (me.role == Role.RECORDER && !m.createdBy.id.equals(me.id)) {
            throw new ForbiddenException("无权查看该纪要");
        }
        return toDto(m);
    }

    /* ----------------------------- 起草 / 修订 ----------------------------- */

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    public MinutesDto create(@RestForm("title") String title,
                             @RestForm("classification") String classification,
                             @RestForm("body") String body,
                             @RestForm("files") List<FileUpload> files) {
        Employee me = currentUser.requireRole(Role.RECORDER);
        MinutesForm form = new MinutesForm();
        form.title = title;
        form.classification = classification;
        form.body = body;
        form.files = files;
        return toDto(service.createDraft(me, form));
    }

    @PUT
    @Path("/{id}/revise")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    public MinutesDto revise(@PathParam("id") Long id,
                             @RestForm("title") String title,
                             @RestForm("classification") String classification,
                             @RestForm("body") String body,
                             @RestForm("files") List<FileUpload> files) {
        Employee me = currentUser.requireRole(Role.RECORDER);
        MinutesForm form = new MinutesForm();
        form.title = title;
        form.classification = classification;
        form.body = body;
        form.files = files;
        return toDto(service.revise(id, me, form));
    }

    public record UsernamesRequest(List<String> usernames) {
    }

    public record CommentRequest(String comment) {
    }

    @POST
    @Path("/{id}/submit")
    @Consumes(MediaType.APPLICATION_JSON)
    public Map<String, String> submit(@PathParam("id") Long id, UsernamesRequest req) {
        Employee me = currentUser.requireRole(Role.RECORDER);
        service.submitForApproval(id, me, req != null ? req.usernames() : null);
        return Map.of("status", "submitted");
    }

    /* ------------------------------- 审批 ------------------------------- */

    @GET
    @Path("/{id}/approval-preview")
    @Transactional
    public ApprovalPreview preview(@PathParam("id") Long id) {
        Employee me = currentUser.requireRole(Role.APPROVER);
        Minutes m = Minutes.findById(id);
        if (m == null) {
            throw new NotFoundException("纪要不存在");
        }
        if (m.status != MinutesStatus.PENDING) {
            throw new BadRequestException("该纪要当前不在待审批状态");
        }
        List<PreviewRow> rows = new ArrayList<>();
        for (ProposedRecipient p : ProposedRecipient.<ProposedRecipient>list("minutes", m)) {
            Employee e = p.employee;
            boolean eligible = e.active && e.clearanceLevel >= m.classification.getLevel();
            String note = eligible ? "资质达标" : "当前密级资质不足，将从最终名单剔除";
            rows.add(new PreviewRow(e.username, e.name, e.department, e.position,
                    e.clearanceLevel, eligible, note));
        }
        List<RecipientDto> grants = Grant.<Grant>find("minutes", m).list().stream()
                .map(gg -> new RecipientDto(gg.employee.username, gg.employee.name,
                            gg.grantedDepartment, gg.grantedPosition, gg.grantedClearanceLevel,
                            gg.active, gg.revokeReason != null ? gg.revokeReason.name() : null))
                .toList();
        return new ApprovalPreview(toDto(m), rows, grants);
    }

    @POST
    @Path("/{id}/approve")
    @Consumes(MediaType.APPLICATION_JSON)
    public Map<String, String> approve(@PathParam("id") Long id, UsernamesRequest req) {
        Employee me = currentUser.requireRole(Role.APPROVER);
        service.approve(id, me, req != null ? req.usernames() : null);
        return Map.of("status", "approved");
    }

    @POST
    @Path("/{id}/reject")
    @Consumes(MediaType.APPLICATION_JSON)
    public Map<String, String> reject(@PathParam("id") Long id, CommentRequest req) {
        Employee me = currentUser.requireRole(Role.APPROVER);
        service.reject(id, me, req != null ? req.comment() : null);
        return Map.of("status", "rejected");
    }

    /** 负责人事后撤回某接收人的授权；旧链接立即失效。 */
    @DELETE
    @Path("/{id}/recipients/{username}")
    public Map<String, String> revoke(@PathParam("id") Long id,
                                      @PathParam("username") String username) {
        Employee me = currentUser.requireRole(Role.APPROVER);
        service.revokeRecipient(id, username, me);
        return Map.of("status", "revoked", "username", username);
    }

    /** 负责人向已发布纪要追加/恢复接收人（调回等场景），补发提醒邮件。 */
    @POST
    @Path("/{id}/recipients/{username}")
    public Map<String, String> grant(@PathParam("id") Long id,
                                     @PathParam("username") String username) {
        Employee me = currentUser.requireRole(Role.APPROVER);
        service.grantRecipient(id, username, me);
        return Map.of("status", "granted", "username", username);
    }

    /** 审批人/管理员审查附件（非员工分发通道）。 */
    @GET
    @Path("/attachments/{attachmentId}/review")
    @jakarta.transaction.Transactional
    public jakarta.ws.rs.core.Response reviewAttachment(
            @PathParam("attachmentId") Long attachmentId) {
        Employee me = currentUser.require();
        Attachment a = Attachment.findById(attachmentId);
        if (a == null) {
            throw new NotFoundException("附件不存在");
        }
        Minutes m = a.version.minutes;
        if (me.role == Role.STAFF
                || (me.role == Role.RECORDER && !m.createdBy.id.equals(me.id))) {
            throw new ForbiddenException("无权审查该附件");
        }
        java.nio.file.Path file = storage.resolve(a.storagePath);
        if (!java.nio.file.Files.exists(file)) {
            throw new NotFoundException("附件文件缺失");
        }
        byte[] content;
        try {
            content = java.nio.file.Files.readAllBytes(file);
        } catch (java.io.IOException e) {
            throw new WebApplicationException("读取附件失败", 500);
        }
        String disposition = "attachment; filename*=UTF-8''"
                + java.net.URLEncoder.encode(a.filename, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        return jakarta.ws.rs.core.Response.ok(content)
                .type(a.contentType != null ? a.contentType : MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", disposition)
                .header("X-Content-Type-Options", "nosniff")
                .build();
    }

    @GET
    @Path("/{id}/grants")
    @Transactional
    public List<RecipientDto> grants(@PathParam("id") Long id) {
        Employee me = currentUser.require();
        Minutes m = Minutes.findById(id);
        if (m == null) {
            throw new NotFoundException("纪要不存在");
        }
        if (me.role == Role.STAFF) {
            throw new ForbiddenException("无权查看授权名单");
        }
        return Grant.<Grant>find("minutes", m).list().stream()
                .map(g -> new RecipientDto(g.employee.username, g.employee.name,
                        g.grantedDepartment, g.grantedPosition, g.grantedClearanceLevel,
                        g.active, g.revokeReason != null ? g.revokeReason.name() : null))
                .toList();
    }
}
