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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "admin_dummy_user")
public class AdminDummyUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dummy_key", nullable = false, length = 80)
    private String dummyKey;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "scenario_summary", nullable = false, length = 500)
    private String scenarioSummary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_user_id", nullable = false)
    private AppUser appUser;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getDummyKey() {
        return dummyKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getScenarioSummary() {
        return scenarioSummary;
    }

    public AppUser getAppUser() {
        return appUser;
    }

    public boolean isActive() {
        return isActive;
    }
}

