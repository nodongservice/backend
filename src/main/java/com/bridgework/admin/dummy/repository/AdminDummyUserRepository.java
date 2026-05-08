package com.bridgework.admin.dummy.repository;

import com.bridgework.admin.dummy.entity.AdminDummyUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminDummyUserRepository extends JpaRepository<AdminDummyUser, Long> {

    @EntityGraph(attributePaths = {"appUser"})
    Optional<AdminDummyUser> findByDummyKeyAndIsActiveTrue(String dummyKey);

    List<AdminDummyUser> findByIsActiveTrueOrderByIdAsc();
}

