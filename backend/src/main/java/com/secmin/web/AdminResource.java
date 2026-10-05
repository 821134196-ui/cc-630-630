package com.secmin.web;

import com.secmin.domain.*;
import com.secmin.service.MinutesService;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.*;

/**
 * 分发记录 / 审计 / 模拟邮件查询，及管理员撤销个人授权。
 */
@Path("/api/admin")
@Produces(MediaType.APPLICATION_JSON)
public class AdminResource {

    @Inject
    EntityManager em;

    @Inject
    CurrentUser current;

    @Inject
    MinutesService minutesService;

    /** 历史分发记录（含已撤销/已失效，永久保留）。 */
    @GET
    @Path("/grants")
    public List<Map<String, Object>> grants(@QueryParam("minutesId") Long minutesId) {
        AppUser u = current.require();
        allowStaff(u);
        String ql = "select g from DistributionGrant g"
                + (minutesId != null ? " where g.minutesId = :mid" : "")
                + " order by g.id desc";
        var q = em.createQuery(ql, DistributionGrant.class);
        if (minutesId != null) {
            q.setParameter("mid", minutesId);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (DistributionGrant g : q.getResultList()) {
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("id", g.getId());
            dto.put("minutesId", g.getMinutesId());
            dto.put("versionId", g.getVersionId());
            dto.put("userId", g.getUserId());
            AppUser u2 = em.find(AppUser.class, g.getUserId());
            dto.put("userAccount", u2 == null ? null : u2.getAccount());
            dto.put("userName", u2 == null ? null : u2.getName());
            dto.put("token", g.getToken());
            dto.put("link", "/#/view/" + g.getToken());
            dto.put("revoked", g.isRevoked());
            dto.put("revokedAt", g.getRevokedAt() == null ? null : g.getRevokedAt().toString());
            dto.put("createdAt", g.getCreatedAt().toString());
            dto.put("lastAccessAt", g.getLastAccessAt() == null ? null
                    : g.getLastAccessAt().toString());
            Minutes m = em.find(Minutes.class, g.getMinutesId());
            dto.put("minutesTitle", m == null ? null : m.getTitle());
            MinutesVersion v = em.find(MinutesVersion.class, g.getVersionId());
            dto.put("versionNo", v == null ? null : v.getVersionNo());
            dto.put("versionState", v == null ? null : v.getState().name());
            dto.put("versionStateLabel", v == null ? null : v.getState().getLabel());
            // 当前是否仍可访问（用于展示“旧链接已失效”的实时状态）
            dto.put("currentlyAccessible", isCurrentlyAccessible(g, u2, v));
            out.add(dto);
        }
        return out;
    }

    private Boolean isCurrentlyAccessible(DistributionGrant g, AppUser user, MinutesVersion v) {
        if (g.isRevoked() || user == null || !user.isEnabled() || v == null
                || v.getState() != VersionState.APPROVED) {
            return false;
        }
        if (!com.secmin.service.AccessControlService
                .parseIdList(v.getApprovedRecipientIds() == null ? ""
                        : v.getApprovedRecipientIds()).contains(user.getId())) {
            return false;
        }
        return user.getClearanceLevel() >= v.getClassification().getLevel();
    }

    /** 管理员撤销某个人的定向授权（旧链接立即失效，记录保留）。 */
    @POST
    @Path("/grants/{id}/revoke")
    @Transactional
    public Map<String, Object> revoke(@PathParam("id") Long id) {
        AppUser u = current.require();
        if (u.getRole() != Role.ADMIN) {
            throw new WebException(403, "仅管理员可撤销个人授权");
        }
        minutesService.revokeGrant(id, u.getId());
        return Map.of("revoked", true, "id", id);
    }

    /** 访问审计：放行与拒绝均记录。 */
    @GET
    @Path("/audit")
    public List<Map<String, Object>> audit(@QueryParam("minutesId") Long minutesId,
                                           @QueryParam("result") String result) {
        AppUser u = current.require();
        allowStaff(u);
        StringBuilder ql = new StringBuilder("select a from AccessAudit a where 1=1");
        if (minutesId != null) {
            ql.append(" and a.minutesId = :mid");
        }
        if (result != null && !result.isBlank()) {
            ql.append(" and a.result = :res");
        }
        ql.append(" order by a.id desc");
        var q = em.createQuery(ql.toString(), AccessAudit.class);
        if (minutesId != null) {
            q.setParameter("mid", minutesId);
        }
        if (result != null && !result.isBlank()) {
            q.setParameter("res", AuditResult.valueOf(result));
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (AccessAudit a : q.setMaxResults(500).getResultList()) {
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("id", a.getId());
            dto.put("at", a.getAt().toString());
            dto.put("userId", a.getUserId());
            dto.put("userAccount", a.getUserAccount());
            dto.put("minutesId", a.getMinutesId());
            dto.put("versionId", a.getVersionId());
            dto.put("attachmentId", a.getAttachmentId());
            dto.put("action", a.getAction().name());
            dto.put("actionLabel", a.getAction().getLabel());
            dto.put("result", a.getResult().name());
            dto.put("resultLabel", a.getResult().getLabel());
            dto.put("reason", a.getReason());
            dto.put("token", a.getToken() == null ? null
                    : a.getToken().substring(0, Math.min(8, a.getToken().length())) + "…");
            out.add(dto);
        }
        return out;
    }

    /** 模拟邮件：验证“邮件不含正文”，且能看到发给谁的提醒。 */
    @GET
    @Path("/mails")
    public List<Map<String, Object>> mails(@QueryParam("account") String account) {
        AppUser u = current.require();
        allowStaff(u);
        String ql = "select m from SimulatedMail m"
                + (account != null && !account.isBlank() ? " where m.toAccount = :a" : "")
                + " order by m.id desc";
        var q = em.createQuery(ql, SimulatedMail.class);
        if (account != null && !account.isBlank()) {
            q.setParameter("a", account);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (SimulatedMail m : q.setMaxResults(200).getResultList()) {
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("id", m.getId());
            dto.put("sentAt", m.getSentAt().toString());
            dto.put("toAccount", m.getToAccount());
            dto.put("toEmail", m.getToEmail());
            dto.put("subject", m.getSubject());
            dto.put("bodyText", m.getBodyText());
            dto.put("minutesId", m.getMinutesId());
            dto.put("versionId", m.getVersionId());
            dto.put("classification", m.getClassification());
            dto.put("filePath", m.getFilePath());
            // 邮件安全自检：正文内容不应出现在邮件里
            MinutesVersion v = m.getVersionId() == null ? null
                    : em.find(MinutesVersion.class, m.getVersionId());
            boolean leaks = v != null && v.getBodyText() != null && !v.getBodyText().isBlank()
                    && m.getBodyText().contains(v.getBodyText());
            dto.put("containsBody", leaks);
            out.add(dto);
        }
        return out;
    }

    private void allowStaff(AppUser u) {
        if (u.getRole() == Role.EMPLOYEE) {
            throw new WebException(403, "审计与分发记录仅管理侧可查看");
        }
    }
}
