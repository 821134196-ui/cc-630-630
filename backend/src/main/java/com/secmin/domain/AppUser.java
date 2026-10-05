package com.secmin.domain;

import jakarta.persistence.*;

/**
 * 员工。currentPost / clearanceLevel 表示当前岗位与岗位密级。
 * 转岗 = 直接更新这两个字段，访问时实时读取，因此转岗降级后旧链接立即失效。
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String account;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "current_post", nullable = false)
    private String currentPost;

    @Column(name = "clearance_level", nullable = false)
    private int clearanceLevel;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "post_updated_at")
    private java.time.Instant postUpdatedAt;

    public AppUser() {
    }

    public AppUser(String account, String name, String email, Role role,
                   String currentPost, int clearanceLevel) {
        this.account = account;
        this.name = name;
        this.email = email;
        this.role = role;
        this.currentPost = currentPost;
        this.clearanceLevel = clearanceLevel;
        this.postUpdatedAt = java.time.Instant.now();
    }

    public Long getId() { return id; }
    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public String getCurrentPost() { return currentPost; }
    public void setCurrentPost(String currentPost) { this.currentPost = currentPost; }
    public int getClearanceLevel() { return clearanceLevel; }
    public void setClearanceLevel(int clearanceLevel) { this.clearanceLevel = clearanceLevel; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public java.time.Instant getPostUpdatedAt() { return postUpdatedAt; }
    public void setPostUpdatedAt(java.time.Instant postUpdatedAt) { this.postUpdatedAt = postUpdatedAt; }
}
