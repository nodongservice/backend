package com.bridgework.sync.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.sql.Types;
import java.time.OffsetDateTime;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(
        name = "public_data_record",
        uniqueConstraints = @UniqueConstraint(name = "uk_public_data_source_external", columnNames = {"source_type", "external_id"})
)
public class PublicDataRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 64)
    private PublicDataSourceType sourceType;

    @Column(name = "external_id", nullable = false, length = 128)
    private String externalId;

    @Column(name = "payload_json", nullable = false)
    private String payloadJson;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "payload_hash", nullable = false, columnDefinition = "CHAR(64)")
    private String payloadHash;

    @Column(name = "raw_fetched_at", nullable = false)
    private OffsetDateTime rawFetchedAt;

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

    public PublicDataSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(PublicDataSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public void setPayloadHash(String payloadHash) {
        this.payloadHash = payloadHash;
    }

    public OffsetDateTime getRawFetchedAt() {
        return rawFetchedAt;
    }

    public void setRawFetchedAt(OffsetDateTime rawFetchedAt) {
        this.rawFetchedAt = rawFetchedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
