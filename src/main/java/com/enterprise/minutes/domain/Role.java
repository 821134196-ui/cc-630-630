package com.enterprise.minutes.domain;

/** 系统角色（演示系统使用单一角色）。 */
public enum Role {
    /** 记录员：起草、修订、提交。 */
    RECORDER,
    /** 负责人：审批实际接收名单、撤回授权。 */
    APPROVER,
    /** 管理员：人事调动等。 */
    ADMIN,
    /** 普通员工：仅可访问被授权的纪要。 */
    STAFF
}
