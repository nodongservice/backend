package com.bridgework.admin.auth.repository;

import com.bridgework.admin.auth.entity.AdminAccount;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminAccountRepository extends JpaRepository<AdminAccount, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select admin from AdminAccount admin where admin.loginId = :loginId")
    Optional<AdminAccount> findForUpdateByLoginId(@Param("loginId") String loginId);
}
