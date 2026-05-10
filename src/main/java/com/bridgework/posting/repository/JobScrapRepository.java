package com.bridgework.posting.repository;

import com.bridgework.posting.entity.JobScrap;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobScrapRepository extends JpaRepository<JobScrap, Long> {

    boolean existsByUserIdAndPostingId(Long userId, Long postingId);

    Optional<JobScrap> findByUserIdAndPostingId(Long userId, Long postingId);

    long deleteByUserIdAndPostingId(Long userId, Long postingId);
}
