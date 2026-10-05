package com.enterprise.minutes.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

/** 提交审批时记录员建议、待负责人确认的候选接收人。 */
@Entity
@Table(name = "proposed_recipient",
        uniqueConstraints = @UniqueConstraint(columnNames = {"minutes_id", "employee_id"}))
public class ProposedRecipient extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public Minutes minutes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public Employee employee;

    public ProposedRecipient() {
    }

    public ProposedRecipient(Minutes minutes, Employee employee) {
        this.minutes = minutes;
        this.employee = employee;
    }
}
