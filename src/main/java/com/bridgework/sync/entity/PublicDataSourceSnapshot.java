package com.bridgework.sync.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "public_data_source_snapshot")
public class PublicDataSourceSnapshot {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 64)
    private PublicDataSourceType sourceType;

    @Column(name = "latest_revision", nullable = false, length = 200)
    private String latestRevision;

    @Column(name = "latest_file_name", nullable = false, length = 255)
    private String latestFileName;

    @Column(name = "latest_modified_date", nullable = false, length = 10)
    private String latestModifiedDate;

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

    public PublicDataSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(PublicDataSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public String getLatestRevision() {
        return latestRevision;
    }

    public void setLatestRevision(String latestRevision) {
        this.latestRevision = latestRevision;
    }

    public String getLatestFileName() {
        return latestFileName;
    }

    public void setLatestFileName(String latestFileName) {
        this.latestFileName = latestFileName;
    }

    public String getLatestModifiedDate() {
        return latestModifiedDate;
    }

    public void setLatestModifiedDate(String latestModifiedDate) {
        this.latestModifiedDate = latestModifiedDate;
    }
}
