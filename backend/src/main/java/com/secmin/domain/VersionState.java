package com.secmin.domain;

/**
 * 版本流转：记录员提交 -&gt; PENDING；负责人审批 -&gt; APPROVED（旧审批版本变 SUPERSEDED）；
 * 负责人撤回审批 -&gt; WITHDRAWN（此时该纪要不存在任何可访问版本，全部链接立即失效）。
 */
public enum VersionState {
    PENDING("待审批"),
    APPROVED("已审批"),
    SUPERSEDED("已被新版替代"),
    WITHDRAWN("审批已撤回");

    private final String label;

    VersionState(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
