package com.secmin.domain;

/**
 * 密级。level 越大越敏感。
 * 员工当前岗位密级 {@link AppUser#clearanceLevel} 必须 &gt;= 纪要版本密级才可访问。
 */
public enum Classification {
    PUBLIC(1, "公开"),
    INTERNAL(2, "内部"),
    CONFIDENTIAL(3, "秘密"),
    SECRET(4, "机密");

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

    public static Classification ofLevel(int level) {
        for (Classification c : values()) {
            if (c.level == level) {
                return c;
            }
        }
        throw new IllegalArgumentException("未知密级: " + level);
    }
}
