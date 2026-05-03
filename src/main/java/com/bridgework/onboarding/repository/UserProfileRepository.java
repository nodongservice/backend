package com.bridgework.onboarding.repository;

import com.bridgework.onboarding.entity.UserProfile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    List<UserProfile> findByUser_IdOrderByIsDefaultDescUpdatedAtDesc(Long userId);

    Optional<UserProfile> findByIdAndUser_Id(Long profileId, Long userId);

    Optional<UserProfile> findByUser_IdAndIsDefaultTrue(Long userId);

    long countByUser_Id(Long userId);
}
