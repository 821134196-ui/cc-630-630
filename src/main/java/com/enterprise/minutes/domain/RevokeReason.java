package com.enterprise.minutes.domain;

/** 授权失效原因。历史授权与分发记录永久保留。 */
public enum RevokeReason {
    NONE,
    /** 负责人在审批/再次发布时将该员工移出接收名单，或事后撤回。 */
    REMOVED_BY_APPROVER,
    /** 员工转岗：原岗位上的授权全部失效，旧链接立即不可用。 */
    TRANSFERRED,
    /** 员工当前岗位密级资质不再满足纪要密级。 */
    CLEARANCE_DOWNGRADE
}
