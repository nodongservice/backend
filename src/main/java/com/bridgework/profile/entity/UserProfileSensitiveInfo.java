package com.bridgework.profile.entity;

import com.bridgework.common.security.EncryptedStringAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_profile_sensitive_info")
public class UserProfileSensitiveInfo {

    @Id
    @Column(name = "profile_id")
    private Long profileId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id")
    private UserProfile profile;

    @Convert(converter = EncryptedStringAttributeConverter.class)
    @Column(name = "required_supports_json", nullable = false, columnDefinition = "TEXT")
    private String requiredSupportsJson = "[]";

    @Convert(converter = EncryptedStringAttributeConverter.class)
    @Column(name = "disability_type", columnDefinition = "TEXT")
    private String disabilityType;

    @Convert(converter = EncryptedStringAttributeConverter.class)
    @Column(name = "disability_severity", columnDefinition = "TEXT")
    private String disabilitySeverity;

    @Convert(converter = EncryptedStringAttributeConverter.class)
    @Column(name = "disability_registered_yn", columnDefinition = "TEXT")
    private String disabilityRegisteredYn;

    @Convert(converter = EncryptedStringAttributeConverter.class)
    @Column(name = "sensitive_info_consent_yn", columnDefinition = "TEXT")
    private String sensitiveInfoConsentYn;

    @Convert(converter = EncryptedStringAttributeConverter.class)
    @Column(name = "disability_description", columnDefinition = "TEXT")
    private String disabilityDescription;

    @Convert(converter = EncryptedStringAttributeConverter.class)
    @Column(name = "assistive_devices", columnDefinition = "TEXT")
    private String assistiveDevices;

    @Convert(converter = EncryptedStringAttributeConverter.class)
    @Column(name = "work_support_requirements", columnDefinition = "TEXT")
    private String workSupportRequirements;

    void setProfile(UserProfile profile) {
        this.profile = profile;
    }

    public void update(String requiredSupportsJson,
                       String disabilityType,
                       String disabilitySeverity,
                       Boolean disabilityRegisteredYn,
                       Boolean sensitiveInfoConsentYn,
                       String disabilityDescription,
                       String assistiveDevices,
                       String workSupportRequirements) {
        this.requiredSupportsJson = requiredSupportsJson == null ? "[]" : requiredSupportsJson;
        this.disabilityType = disabilityType;
        this.disabilitySeverity = disabilitySeverity;
        this.disabilityRegisteredYn = disabilityRegisteredYn == null ? null : disabilityRegisteredYn.toString();
        this.sensitiveInfoConsentYn = sensitiveInfoConsentYn == null ? null : sensitiveInfoConsentYn.toString();
        this.disabilityDescription = disabilityDescription;
        this.assistiveDevices = assistiveDevices;
        this.workSupportRequirements = workSupportRequirements;
    }

    public void anonymize() {
        this.requiredSupportsJson = "[]";
        this.disabilityType = null;
        this.disabilitySeverity = null;
        this.disabilityRegisteredYn = null;
        this.sensitiveInfoConsentYn = null;
        this.disabilityDescription = null;
        this.assistiveDevices = null;
        this.workSupportRequirements = null;
    }

    public String getRequiredSupportsJson() {
        return requiredSupportsJson;
    }

    public String getDisabilityType() {
        return disabilityType;
    }

    public String getDisabilitySeverity() {
        return disabilitySeverity;
    }

    public Boolean getDisabilityRegisteredYn() {
        return disabilityRegisteredYn == null ? null : Boolean.valueOf(disabilityRegisteredYn);
    }

    public Boolean getSensitiveInfoConsentYn() {
        return sensitiveInfoConsentYn == null ? null : Boolean.valueOf(sensitiveInfoConsentYn);
    }

    public String getDisabilityDescription() {
        return disabilityDescription;
    }

    public String getAssistiveDevices() {
        return assistiveDevices;
    }

    public String getWorkSupportRequirements() {
        return workSupportRequirements;
    }
}
