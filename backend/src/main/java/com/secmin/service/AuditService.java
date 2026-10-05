package com.secmin.service;

import com.secmin.domain.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Instant;

/**
 * 审计写入服务。
 *
 * 访问拒绝发生时业务流程随后抛出 {@link AccessDeniedException}，若审计与业务同事务，
 * 拒绝记录会随回滚消失。这里的写入方法各自在独立短事务中提交（访问校验本身非事务），
 * 保证“拒绝也留痕”，且不与外层事务产生嵌套（SQLite 单写连接下避免锁冲突）。
 */
@ApplicationScoped
public class AuditService {

    @Inject
    EntityManager em;

    /** 独立记录一条审计（放行或拒绝）。在无事务上下文中调用时自行开启并提交。 */
    @Transactional
    public void record(AppUser user, Minutes minutes, MinutesVersion version, Long attachmentId,
                       AuditAction action, AuditResult result, String reason, String token) {
        doRecord(user, minutes, version, attachmentId, action, result, reason, token);
    }

    /** 放行时顺带刷新最近访问时间，并落一条 GRANTED 审计（同一事务提交）。 */
    @Transactional
    public void recordGranted(DistributionGrant grant, AppUser user, Minutes minutes,
                              MinutesVersion version, Long attachmentId, AuditAction action) {
        DistributionGrant attached = em.merge(grant);
        attached.setLastAccessAt(Instant.now());
        doRecord(user, minutes, version, attachmentId, action, AuditResult.GRANTED, null,
                attached.getToken());
    }

    private void doRecord(AppUser user, Minutes minutes, MinutesVersion version, Long attachmentId,
                          AuditAction action, AuditResult result, String reason, String token) {
        AccessAudit a = new AccessAudit();
        a.setAt(Instant.now());
        if (user != null) {
            a.setUserId(user.getId());
            a.setUserAccount(user.getAccount());
        }
        if (minutes != null) {
            a.setMinutesId(minutes.getId());
        }
        if (version != null) {
            a.setVersionId(version.getId());
        }
        a.setAttachmentId(attachmentId);
        a.setAction(action);
        a.setResult(result);
        a.setReason(reason);
        a.setToken(token);
        em.persist(a);
        em.flush();
    }
}
