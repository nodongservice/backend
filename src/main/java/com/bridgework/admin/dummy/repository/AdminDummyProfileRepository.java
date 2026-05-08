package com.bridgework.admin.dummy.repository;

import com.bridgework.admin.dummy.entity.AdminDummyProfile;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminDummyProfileRepository extends JpaRepository<AdminDummyProfile, Long> {

    @EntityGraph(attributePaths = {"profile", "profile.user"})
    List<AdminDummyProfile> findByDummyUser_IdOrderBySortOrderAscIdAsc(Long dummyUserId);
}

