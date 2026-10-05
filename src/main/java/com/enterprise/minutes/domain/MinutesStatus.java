package com.enterprise.minutes.domain;

/** 纪要生命周期状态。 */
public enum MinutesStatus {
    /** 起草中：记录员上传了正文+附件，尚未提交审批。 */
    DRAFT,
    /** 待审批：负责人可预览最终接收名单并决定批准/驳回。 */
    PENDING,
    /** 已批准发布：仅当前发布版本可被授权员工访问。 */
    APPROVED,
    /** 审批驳回，记录员可修改后重新提交。 */
    REJECTED
}
