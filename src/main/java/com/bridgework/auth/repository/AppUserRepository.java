package com.bridgework.auth.repository;

import com.bridgework.auth.entity.AppUser;
import com.bridgework.auth.entity.SocialProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByEmail(String email);
}
