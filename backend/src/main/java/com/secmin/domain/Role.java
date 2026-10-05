package com.secmin.domain;

public enum Role {
    SECRETARY("记录员"),
    APPROVER("负责人"),
    EMPLOYEE("员工"),
    ADMIN("管理员");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
