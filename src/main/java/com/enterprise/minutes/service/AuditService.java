package com.enterprise.minutes.service;

import com.enterprise.minutes.domain.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * 访问审计落库。所有正文访问、附件下载、审批、撤回、转岗均记录。
 *
 * 使用普通 REQUIRED 事务：成功路径随业务事务一起提交；
 * 拒绝访问的审计由资源层在业务检查事务之外调用，独立提交，
 * 因此即使随后请求以 403 结束、无其他事务可回滚，拒绝记录也一定保留。
 */
@ApplicationScoped
public class AuditService {

    @Transactional
    public void log(Employee employee, Long minutesId, Long attachmentId,
                    AuditAction action, boolean allowed, String reason, String linkToken) {
        AuditLog l = new AuditLog();
        l.employee = employee;
        l.username = employee != null ? employee.username : "anonymous";
        l.minutes = minutesId != null ? Minutes.findById(minutesId) : null;
        l.attachment = attachmentId != null ? Attachment.findById(attachmentId) : null;
        l.action = action;
        l.allowed = allowed;
        l.reason = reason;
        l.linkToken = linkToken;
        l.persist();
    }
}
