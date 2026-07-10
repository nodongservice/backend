package com.bridgework.posting.dto;

import com.bridgework.posting.entity.PostingFeedbackReaction;
import java.time.OffsetDateTime;

public record PostingFeedbackCreateResponseDto(
        Long feedbackId,
        Long postingId,
        PostingFeedbackReaction reaction,
        OffsetDateTime submittedAt
) {
}
