package com.enterprise.minutes.bootstrap;

import com.enterprise.minutes.domain.*;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;

/** 初始化演示数据：记录员、负责人、管理员与不同密级资质的员工。 */
@ApplicationScoped
public class DataSeeder {

    @Transactional
    public void seed(@Observes StartupEvent ev) {
        if (Employee.count() > 0) {
            return;
        }
        create("recorder", "王记录", "recorder@example.com", Role.RECORDER,
                "综合办", "记录员", 4);
        create("approver", "李负责", "approver@example.com", Role.APPROVER,
                "管理层", "部门负责人", 4);
        create("admin", "赵管理", "admin@example.com", Role.ADMIN,
                "人事部", "系统管理员", 4);
        // 普通员工：不同密级资质
        create("zhang", "张三", "zhang@example.com", Role.STAFF, "研发部", "高级工程师", 3);
        create("wang", "王五", "wang@example.com", Role.STAFF, "研发部", "工程师", 2);
        create("chen", "陈敏", "chen@example.com", Role.STAFF, "市场部", "市场专员", 2);
        create("liu", "刘洋", "liu@example.com", Role.STAFF, "战略部", "战略分析师", 4);
    }

    private void create(String username, String name, String email, Role role,
                        String dept, String position, int clearance) {
        Employee e = new Employee();
        e.username = username;
        e.name = name;
        e.email = email;
        e.role = role;
        e.department = dept;
        e.position = position;
        e.clearanceLevel = clearance;
        e.persist();
    }
}
