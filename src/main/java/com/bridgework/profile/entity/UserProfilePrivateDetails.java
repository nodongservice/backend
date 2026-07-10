package com.bridgework.profile.entity;

import com.bridgework.auth.entity.GenderType;
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
import java.time.LocalDate;

@Entity
@Table(name = "user_profile_private_details")
public class UserProfilePrivateDetails {

    @Id
    @Column(name = "profile_id")
    private Long profileId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id")
    private UserProfile profile;

    @Convert(converter = EncryptedStringAttributeConverter.class)
    @Column(name = "full_name", nullable = false, columnDefinition = "TEXT")
    private String fullName;

    @Convert(converter = EncryptedStringAttributeConverter.class)
    @Column(name = "contact_phone", nullable = false, columnDefinition = "TEXT")
    private String contactPhone;

    @Convert(converter = EncryptedStringAttributeConverter.class)
    @Column(name = "contact_email_override", columnDefinition = "TEXT")
    private String contactEmailOverride;

    @Convert(converter = EncryptedStringAttributeConverter.class)
    @Column(name = "birth_date", columnDefinition = "TEXT")
    private String birthDate;

    @Convert(converter = EncryptedStringAttributeConverter.class)
    @Column(name = "gender_type", columnDefinition = "TEXT")
    private String genderType;

    @Convert(converter = EncryptedStringAttributeConverter.class)
    @Column(name = "detail_address", columnDefinition = "TEXT")
    private String detailAddress;

    @Convert(converter = EncryptedStringAttributeConverter.class)
    @Column(name = "emergency_contact", columnDefinition = "TEXT")
    private String emergencyContact;

    @Convert(converter = EncryptedStringAttributeConverter.class)
    @Column(name = "home_lat", columnDefinition = "TEXT")
    private String homeLat;

    @Convert(converter = EncryptedStringAttributeConverter.class)
    @Column(name = "home_lng", columnDefinition = "TEXT")
    private String homeLng;

    @Convert(converter = EncryptedStringAttributeConverter.class)
    @Column(name = "home_geocoded_address", columnDefinition = "TEXT")
    private String homeGeocodedAddress;

    void setProfile(UserProfile profile) {
        this.profile = profile;
    }

    public void update(String fullName,
                       String contactPhone,
                       String contactEmailOverride,
                       LocalDate birthDate,
                       GenderType genderType,
                       String detailAddress,
                       String emergencyContact) {
        this.fullName = fullName;
        this.contactPhone = contactPhone;
        this.contactEmailOverride = contactEmailOverride;
        this.birthDate = birthDate == null ? null : birthDate.toString();
        this.genderType = genderType == null ? null : genderType.name();
        this.detailAddress = detailAddress;
        this.emergencyContact = emergencyContact;
    }

    public void updateHomeCoordinates(Double homeLat, Double homeLng, String homeGeocodedAddress) {
        this.homeLat = homeLat == null ? null : String.valueOf(homeLat);
        this.homeLng = homeLng == null ? null : String.valueOf(homeLng);
        this.homeGeocodedAddress = homeGeocodedAddress;
    }

    public void anonymize(String anonymizedContactEmail) {
        this.fullName = "탈퇴회원";
        this.contactPhone = "00000000000";
        this.contactEmailOverride = anonymizedContactEmail;
        this.birthDate = LocalDate.of(1900, 1, 1).toString();
        this.genderType = null;
        this.detailAddress = "탈퇴회원";
        this.emergencyContact = null;
        this.homeLat = null;
        this.homeLng = null;
        this.homeGeocodedAddress = null;
    }

    public String getFullName() {
        return fullName;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public String getContactEmailOverride() {
        return contactEmailOverride;
    }

    public LocalDate getBirthDate() {
        return birthDate == null ? null : LocalDate.parse(birthDate);
    }

    public GenderType getGenderType() {
        return genderType == null ? null : GenderType.valueOf(genderType);
    }

    public String getDetailAddress() {
        return detailAddress;
    }

    public String getEmergencyContact() {
        return emergencyContact;
    }

    public Double getHomeLat() {
        return homeLat == null ? null : Double.valueOf(homeLat);
    }

    public Double getHomeLng() {
        return homeLng == null ? null : Double.valueOf(homeLng);
    }

    public String getHomeGeocodedAddress() {
        return homeGeocodedAddress;
    }
}
