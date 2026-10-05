package com.enterprise.minutes.domain;

/** 访问审计动作，拒绝访问同样记录。 */
public enum AuditAction {
    VIEW_BODY,
    VIEW_DENIED,
    DOWNLOAD_ATTACHMENT,
    DOWNLOAD_DENIED,
    APPROVE,
    REJECT,
    REVOKE_RECIPIENT,
    TRANSFER
}
