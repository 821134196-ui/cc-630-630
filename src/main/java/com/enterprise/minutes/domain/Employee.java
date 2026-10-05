package com.enterprise.minutes.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 员工。授权核验始终读取该表的【当前】岗位与资质：
 * 转岗（部门/岗位/密级资质变化）后，旧链接在每次打开时重新校验、立即失效。
 */
@Entity
@Table(name = "employee")
public class Employee extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true)
    public String username;

    @Column(nullable = false)
    public String name;

    @Column(nullable = false)
    public String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Role role;

    /** 当前部门 */
    @Column(nullable = false)
    public String department;

    /** 当前岗位 */
    @Column(nullable = false)
    public String position;

    /** 当前岗位的密级资质（1-4），必须 &gt;= 纪要密级才可访问 */
    @Column(nullable = false)
    public int clearanceLevel;

    @Column(nullable = false)
    public boolean active = true;

    @Column(nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    public static Employee findByUsername(String username) {
        return find("username", username).firstResult();
    }
}
