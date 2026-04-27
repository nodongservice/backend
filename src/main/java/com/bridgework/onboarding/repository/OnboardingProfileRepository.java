package com.bridgework.onboarding.repository;

import com.bridgework.onboarding.entity.OnboardingProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OnboardingProfileRepository extends JpaRepository<OnboardingProfile, Long> {

    Optional<OnboardingProfile> findByUser_Id(Long userId);
}
