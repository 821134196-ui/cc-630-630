package com.enterprise.minutes.domain;

/**
 * 纪要密级：level 越高越敏感。
 * 员工 clearanceLevel 必须 &gt;= 纪要密级 level 才可能出现在最终接收名单中。
 */
public enum Classification {
    PUBLIC(1, "公开"),
    INTERNAL(2, "内部"),
    SECRET(3, "机密"),
    TOP_SECRET(4, "绝密");

    private final int level;
    private final String label;

    Classification(int level, String label) {
        this.level = level;
        this.label = label;
    }

    public int getLevel() {
        return level;
    }

    public String getLabel() {
        return label;
    }
}
