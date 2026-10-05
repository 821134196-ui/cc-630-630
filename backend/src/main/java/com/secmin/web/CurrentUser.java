package com.secmin.web;

import com.secmin.domain.AppUser;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * 请求级当前登录用户。演示系统以 X-Account 请求头标识登录身份（前端切换账号），
 * 生产环境应替换为真实会话/JWT——但所有数据访问仍在服务端实时核验，不依赖前端传参。
 */
@RequestScoped
public class CurrentUser {

    @Inject
    EntityManager em;

    private AppUser user;
    private boolean resolved;

    public AppUser get() {
        return user;
    }

    public AppUser require() {
        if (user == null) {
            throw new WebException(401, "未登录或会话已失效");
        }
        return user;
    }

    public void resolve(String account) {
        if (resolved) {
            return;
        }
        resolved = true;
        if (account != null && !account.isBlank()) {
            user = em.createQuery("select u from AppUser u where u.account = :a", AppUser.class)
                    .setParameter("a", account.trim())
                    .getResultList().stream().findFirst().orElse(null);
            if (user != null && !user.isEnabled()) {
                user = null;
            }
        }
    }
}
