package com.bridgework.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "personal_data_access_audit")
public class PersonalDataAccessAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_user_id", nullable = false)
    private Long adminUserId;

    @Column(name = "action_type", nullable = false, length = 80)
    private String actionType;

    @Column(name = "target_user_id")
    private Long targetUserId;

    @Column(name = "target_profile_id")
    private Long targetProfileId;

    @Column(name = "request_ip", length = 120)
    private String requestIp;

    @Column(name = "access_outcome", nullable = false, length = 20)
    private String accessOutcome;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public void setAdminUserId(Long adminUserId) {
        this.adminUserId = adminUserId;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public void setTargetUserId(Long targetUserId) {
        this.targetUserId = targetUserId;
    }

    public void setTargetProfileId(Long targetProfileId) {
        this.targetProfileId = targetProfileId;
    }

    public void setRequestIp(String requestIp) {
        this.requestIp = requestIp;
    }

    public void setAccessOutcome(String accessOutcome) {
        this.accessOutcome = accessOutcome;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
