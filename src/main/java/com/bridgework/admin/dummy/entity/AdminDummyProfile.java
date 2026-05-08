package com.bridgework.admin.dummy.entity;

import com.bridgework.profile.entity.UserProfile;
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
@Table(name = "admin_dummy_profile")
public class AdminDummyProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dummy_user_id", nullable = false)
    private AdminDummyUser dummyUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private UserProfile profile;

    @Column(name = "profile_key", nullable = false, length = 80)
    private String profileKey;

    @Column(name = "profile_label", nullable = false, length = 120)
    private String profileLabel;

    @Column(name = "scenario_summary", nullable = false, length = 500)
    private String scenarioSummary;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

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

    public AdminDummyUser getDummyUser() {
        return dummyUser;
    }

    public UserProfile getProfile() {
        return profile;
    }

    public String getProfileKey() {
        return profileKey;
    }

    public String getProfileLabel() {
        return profileLabel;
    }

    public String getScenarioSummary() {
        return scenarioSummary;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }
}

