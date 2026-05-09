package com.bridgework.auth.repository;

import com.bridgework.auth.entity.AppUser;
import com.bridgework.auth.entity.SocialProvider;
import com.bridgework.auth.entity.UserStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);

    Optional<AppUser> findByIdAndStatus(Long id, UserStatus status);

    List<AppUser> findAllByStatusAndWithdrawalRequestedAtBefore(UserStatus status, OffsetDateTime before);

    boolean existsByEmail(String email);

    @Query(value = """
            SELECT COUNT(*)
            FROM app_user u
            WHERE u.role = 'USER'
              AND u.status = 'ACTIVE'
              AND u.signup_completed = TRUE
              AND NOT EXISTS (
                  SELECT 1
                  FROM admin_dummy_user d
                  WHERE d.app_user_id = u.id
              )
            """, nativeQuery = true)
    long countRealSignedUpUsers();
}
