package com.bridgework.posting.repository;

import com.bridgework.posting.entity.PostingFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostingFeedbackRepository extends JpaRepository<PostingFeedback, Long> {
}
