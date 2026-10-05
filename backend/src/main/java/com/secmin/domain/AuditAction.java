package com.secmin.domain;

public enum AuditAction {
    VIEW_BODY("查看正文"),
    DOWNLOAD_ATTACHMENT("下载附件"),
    OPEN_LINK("打开链接"),
    APPROVE("审批通过"),
    WITHDRAW("撤回审批"),
    REVOKE_GRANT("撤销个人授权");

    private final String label;

    AuditAction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
