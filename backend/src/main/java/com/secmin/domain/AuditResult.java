package com.secmin.domain;

public enum AuditResult {
    GRANTED("放行"),
    DENIED("拒绝");

    private final String label;

    AuditResult(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
