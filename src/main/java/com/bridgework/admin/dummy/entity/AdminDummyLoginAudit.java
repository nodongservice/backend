package com.bridgework.admin.dummy.entity;

import com.bridgework.auth.entity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "admin_dummy_login_audit")
public class AdminDummyLoginAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_user_id", nullable = false)
    private Long adminUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dummy_user_id", nullable = false)
    private AdminDummyUser dummyUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issued_user_id", nullable = false)
    private AppUser issuedUser;

    @Column(name = "request_ip", length = 64)
    private String requestIp;

    @Column(name = "issued_at", nullable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (issuedAt == null) {
            issuedAt = OffsetDateTime.now();
        }
        createdAt = OffsetDateTime.now();
    }

    public void setAdminUserId(Long adminUserId) {
        this.adminUserId = adminUserId;
    }

    public void setDummyUser(AdminDummyUser dummyUser) {
        this.dummyUser = dummyUser;
    }

    public void setIssuedUser(AppUser issuedUser) {
        this.issuedUser = issuedUser;
    }

    public void setRequestIp(String requestIp) {
        this.requestIp = requestIp;
    }

    public void setIssuedAt(OffsetDateTime issuedAt) {
        this.issuedAt = issuedAt;
    }
}

