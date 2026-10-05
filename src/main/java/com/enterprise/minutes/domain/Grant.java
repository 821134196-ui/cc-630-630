package com.enterprise.minutes.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 针对某一份纪要的定向授权记录（实际接收名单，由负责人审批确认）。
 * 授权行永久保留；访问资格每次实时计算 = active &amp;&amp; revokedAt==null
 * 且员工当前 clearanceLevel 仍达标。
 */
@Entity
@Table(name = "grant_recipient",
        uniqueConstraints = @UniqueConstraint(columnNames = {"minutes_id", "employee_id"}))
public class Grant extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public Minutes minutes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public Employee employee;

    /** 授权生效的版本；转岗/撤回后该行保留但 revokedAt 置位 */
    @Column(nullable = false)
    public boolean active = true;

    @Column
    public LocalDateTime revokedAt;

    @Enumerated(EnumType.STRING)
    @Column
    public RevokeReason revokeReason;

    /** 审批时员工所在部门/岗位与资质快照 */
    @Column(nullable = false)
    public String grantedDepartment;

    @Column(nullable = false)
    public String grantedPosition;

    @Column(nullable = false)
    public int grantedClearanceLevel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public Employee grantedBy;

    @Column(nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    public static Grant findActive(Minutes minutes, Employee employee) {
        return find("minutes = ?1 and employee = ?2 and active = true", minutes, employee).firstResult();
    }
}
