package com.secmin.service;

import com.secmin.domain.*;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;

/**
 * 演示种子数据（幂等）：
 *  - chenjing  记录员（可起草各密级纪要）
 *  - lifang    负责人（审批/撤回）
 *  - wangwei   战略部员工，机密(4)
 *  - zhaolei   市场部员工，内部(2)
 *  - sunli     研发部员工，秘密(3)
 *  - admin     管理员（转岗/撤销授权/看审计）
 */
@ApplicationScoped
public class DataSeeder {

    @Inject
    EntityManager em;

    @Transactional
    public void onStart(@Observes StartupEvent ev) {
        Long count = em.createQuery("select count(u) from AppUser u", Long.class)
                .getSingleResult();
        if (count != null && count > 0) {
            return;
        }
        List<AppUser> users = List.of(
                new AppUser("chenjing", "陈静", "chenjing@example.com", Role.SECRETARY,
                        "办公室·记录岗", Classification.SECRET.getLevel()),
                new AppUser("lifang", "李芳", "lifang@example.com", Role.APPROVER,
                        "公司分管负责人", Classification.SECRET.getLevel()),
                new AppUser("wangwei", "王伟", "wangwei@example.com", Role.EMPLOYEE,
                        "战略规划部", Classification.SECRET.getLevel()),
                new AppUser("zhaolei", "赵磊", "zhaolei@example.com", Role.EMPLOYEE,
                        "市场部", Classification.INTERNAL.getLevel()),
                new AppUser("sunli", "孙莉", "sunli@example.com", Role.EMPLOYEE,
                        "研发部", Classification.CONFIDENTIAL.getLevel()),
                new AppUser("admin", "系统管理员", "admin@example.com", Role.ADMIN,
                        "信息安全管理岗", Classification.SECRET.getLevel())
        );
        users.forEach(em::persist);
    }
}
