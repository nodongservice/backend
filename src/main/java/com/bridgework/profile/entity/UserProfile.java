package com.bridgework.profile.entity;

import com.bridgework.auth.entity.GenderType;
import com.bridgework.auth.entity.AppUser;
import com.bridgework.profile.dto.UserProfileUpsertRequestDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "user_profile")
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "desired_job", nullable = false, length = 200)
    private String desiredJob;

    @Column(name = "commute_range", nullable = false, length = 120)
    private String commuteRange;

    @Column(name = "preferred_work_environments_json", nullable = false, columnDefinition = "TEXT")
    private String preferredWorkEnvironmentsJson;

    @Column(name = "avoided_work_environments_json", nullable = false, columnDefinition = "TEXT")
    private String avoidedWorkEnvironmentsJson;

    @Column(name = "required_supports_json", nullable = false, columnDefinition = "TEXT")
    private String requiredSupportsJson;

    @Column(name = "disability_type", nullable = false, length = 120)
    private String disabilityType;

    @Column(name = "career_summary", nullable = false, length = 500)
    private String careerSummary;

    @Column(name = "education_summary", nullable = false, length = 500)
    private String educationSummary;

    @Column(name = "employment_type_summary", nullable = false, length = 200)
    private String employmentTypeSummary;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "contact_phone", nullable = false, length = 32)
    private String contactPhone;

    @Column(name = "contact_email", nullable = false, length = 255)
    private String contactEmail;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender_type", nullable = false, length = 20)
    private GenderType genderType;

    @Column(name = "age_group", length = 50)
    private String ageGroup;

    @Column(name = "residence_region", nullable = false, length = 120)
    private String residenceRegion;

    @Column(name = "detail_address", length = 300)
    private String detailAddress;

    @Column(name = "emergency_contact", length = 100)
    private String emergencyContact;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(name = "highest_education", nullable = false, length = 300)
    private String highestEducation;

    @Column(name = "graduation_status", nullable = false, length = 80)
    private String graduationStatus;

    @Column(name = "major_career", nullable = false, length = 500)
    private String majorCareer;

    @Column(name = "career_detail", columnDefinition = "TEXT")
    private String careerDetail;

    @Column(name = "project_experience", columnDefinition = "TEXT")
    private String projectExperience;

    @Column(name = "career_gap_reason", length = 500)
    private String careerGapReason;

    @Column(name = "target_job", nullable = false, length = 200)
    private String targetJob;

    @Column(name = "skills_json", nullable = false, columnDefinition = "TEXT")
    private String skillsJson;

    @Column(name = "certifications_json", columnDefinition = "TEXT")
    private String certificationsJson;

    @Column(name = "portfolio_url", length = 500)
    private String portfolioUrl;

    @Column(name = "awards", columnDefinition = "TEXT")
    private String awards;

    @Column(name = "trainings", columnDefinition = "TEXT")
    private String trainings;

    @Column(name = "disability_severity", nullable = false, length = 80)
    private String disabilitySeverity;

    @Column(name = "disability_registered_yn", nullable = false)
    private Boolean disabilityRegisteredYn;

    @Column(name = "disability_description", columnDefinition = "TEXT")
    private String disabilityDescription;

    @Column(name = "assistive_devices", columnDefinition = "TEXT")
    private String assistiveDevices;

    @Column(name = "work_support_requirements", columnDefinition = "TEXT")
    private String workSupportRequirements;

    @Column(name = "work_availability", nullable = false, length = 80)
    private String workAvailability;

    @Column(name = "work_types_json", nullable = false, columnDefinition = "TEXT")
    private String workTypesJson;

    @Column(name = "expected_salary", length = 120)
    private String expectedSalary;

    @Column(name = "work_time_preference", length = 200)
    private String workTimePreference;

    @Column(name = "remote_available_yn")
    private Boolean remoteAvailableYn;

    @Column(name = "mobility_range", length = 200)
    private String mobilityRange;

    @Column(name = "self_introduction", nullable = false, columnDefinition = "TEXT")
    private String selfIntroduction;

    @Column(name = "motivation", nullable = false, columnDefinition = "TEXT")
    private String motivation;

    @Column(name = "job_fit_description", columnDefinition = "TEXT")
    private String jobFitDescription;

    @Column(name = "career_goal", columnDefinition = "TEXT")
    private String careerGoal;

    @Column(name = "strengths_weaknesses", columnDefinition = "TEXT")
    private String strengthsWeaknesses;

    @Column(name = "military_service", length = 120)
    private String militaryService;

    @Column(name = "patriotic_veteran_yn")
    private Boolean patrioticVeteranYn;

    @Column(name = "referrer", length = 200)
    private String referrer;

    @Column(name = "sns_url", length = 500)
    private String snsUrl;

    @Column(name = "ai_job_tags_json", nullable = false, columnDefinition = "TEXT")
    private String aiJobTagsJson;

    @Column(name = "ai_environment_tags_json", nullable = false, columnDefinition = "TEXT")
    private String aiEnvironmentTagsJson;

    @Column(name = "ai_support_tags_json", nullable = false, columnDefinition = "TEXT")
    private String aiSupportTagsJson;

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

    public void updateFromRequest(UserProfileUpsertRequestDto request,
                                  String preferredWorkEnvironmentsJson,
                                  String avoidedWorkEnvironmentsJson,
                                  String requiredSupportsJson,
                                  String skillsJson,
                                  String certificationsJson,
                                  String workTypesJson,
                                  String aiJobTagsJson,
                                  String aiEnvironmentTagsJson,
                                  String aiSupportTagsJson) {
        // 입력 DTO와 DB 스냅샷을 동일 순서로 매핑해 필드 누락을 방지한다.
        this.desiredJob = request.desiredJob();
        this.commuteRange = request.commuteRange();
        this.preferredWorkEnvironmentsJson = preferredWorkEnvironmentsJson;
        this.avoidedWorkEnvironmentsJson = avoidedWorkEnvironmentsJson;
        this.requiredSupportsJson = requiredSupportsJson;
        this.disabilityType = enumCode(request.disabilityType());
        this.careerSummary = request.careerSummary();
        this.educationSummary = request.educationSummary();
        this.employmentTypeSummary = request.employmentTypeSummary();

        this.fullName = request.fullName();
        this.contactPhone = request.contactPhone();
        this.contactEmail = request.contactEmail();
        this.birthDate = request.birthDate();
        this.genderType = request.genderType();
        this.ageGroup = request.ageGroup();
        this.residenceRegion = enumCode(request.residenceRegion());
        this.detailAddress = request.detailAddress();
        this.emergencyContact = request.emergencyContact();
        this.profileImageUrl = request.profileImageUrl();

        this.highestEducation = enumCode(request.highestEducation());
        this.graduationStatus = enumCode(request.graduationStatus());
        this.majorCareer = request.majorCareer();
        this.careerDetail = request.careerDetail();
        this.projectExperience = request.projectExperience();
        this.careerGapReason = request.careerGapReason();

        this.targetJob = request.targetJob();
        this.skillsJson = skillsJson;
        this.certificationsJson = certificationsJson;
        this.portfolioUrl = request.portfolioUrl();
        this.awards = request.awards();
        this.trainings = request.trainings();

        this.disabilitySeverity = enumCode(request.disabilitySeverity());
        this.disabilityRegisteredYn = request.disabilityRegisteredYn();
        this.disabilityDescription = request.disabilityDescription();
        this.assistiveDevices = request.assistiveDevices();
        this.workSupportRequirements = request.workSupportRequirements();

        this.workAvailability = enumCode(request.workAvailability());
        this.workTypesJson = workTypesJson;
        this.expectedSalary = request.expectedSalary();
        this.workTimePreference = enumCode(request.workTimePreference());
        this.remoteAvailableYn = request.remoteAvailableYn();
        this.mobilityRange = request.mobilityRange();

        this.selfIntroduction = request.selfIntroduction();
        this.motivation = request.motivation();
        this.jobFitDescription = request.jobFitDescription();
        this.careerGoal = request.careerGoal();
        this.strengthsWeaknesses = request.strengthsWeaknesses();

        this.militaryService = enumCode(request.militaryService());
        this.patrioticVeteranYn = request.patrioticVeteranYn();
        this.referrer = request.referrer();
        this.snsUrl = request.snsUrl();

        this.aiJobTagsJson = aiJobTagsJson;
        this.aiEnvironmentTagsJson = aiEnvironmentTagsJson;
        this.aiSupportTagsJson = aiSupportTagsJson;
    }

    private String enumCode(Enum<?> value) {
        return value == null ? null : value.name();
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public String getDesiredJob() {
        return desiredJob;
    }

    public String getCommuteRange() {
        return commuteRange;
    }

    public String getPreferredWorkEnvironmentsJson() {
        return preferredWorkEnvironmentsJson;
    }

    public String getAvoidedWorkEnvironmentsJson() {
        return avoidedWorkEnvironmentsJson;
    }

    public String getRequiredSupportsJson() {
        return requiredSupportsJson;
    }

    public String getDisabilityType() {
        return disabilityType;
    }

    public String getCareerSummary() {
        return careerSummary;
    }

    public String getEducationSummary() {
        return educationSummary;
    }

    public String getEmploymentTypeSummary() {
        return employmentTypeSummary;
    }

    public String getFullName() {
        return fullName;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public GenderType getGenderType() {
        return genderType;
    }

    public String getAgeGroup() {
        return ageGroup;
    }

    public String getResidenceRegion() {
        return residenceRegion;
    }

    public String getDetailAddress() {
        return detailAddress;
    }

    public String getEmergencyContact() {
        return emergencyContact;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public String getHighestEducation() {
        return highestEducation;
    }

    public String getGraduationStatus() {
        return graduationStatus;
    }

    public String getMajorCareer() {
        return majorCareer;
    }

    public String getCareerDetail() {
        return careerDetail;
    }

    public String getProjectExperience() {
        return projectExperience;
    }

    public String getCareerGapReason() {
        return careerGapReason;
    }

    public String getTargetJob() {
        return targetJob;
    }

    public String getSkillsJson() {
        return skillsJson;
    }

    public String getCertificationsJson() {
        return certificationsJson;
    }

    public String getPortfolioUrl() {
        return portfolioUrl;
    }

    public String getAwards() {
        return awards;
    }

    public String getTrainings() {
        return trainings;
    }

    public String getDisabilitySeverity() {
        return disabilitySeverity;
    }

    public Boolean getDisabilityRegisteredYn() {
        return disabilityRegisteredYn;
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

    public String getWorkAvailability() {
        return workAvailability;
    }

    public String getWorkTypesJson() {
        return workTypesJson;
    }

    public String getExpectedSalary() {
        return expectedSalary;
    }

    public String getWorkTimePreference() {
        return workTimePreference;
    }

    public Boolean getRemoteAvailableYn() {
        return remoteAvailableYn;
    }

    public String getMobilityRange() {
        return mobilityRange;
    }

    public String getSelfIntroduction() {
        return selfIntroduction;
    }

    public String getMotivation() {
        return motivation;
    }

    public String getJobFitDescription() {
        return jobFitDescription;
    }

    public String getCareerGoal() {
        return careerGoal;
    }

    public String getStrengthsWeaknesses() {
        return strengthsWeaknesses;
    }

    public String getMilitaryService() {
        return militaryService;
    }

    public Boolean getPatrioticVeteranYn() {
        return patrioticVeteranYn;
    }

    public String getReferrer() {
        return referrer;
    }

    public String getSnsUrl() {
        return snsUrl;
    }

    public String getAiJobTagsJson() {
        return aiJobTagsJson;
    }

    public String getAiEnvironmentTagsJson() {
        return aiEnvironmentTagsJson;
    }

    public String getAiSupportTagsJson() {
        return aiSupportTagsJson;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
