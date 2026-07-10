package com.bridgework.posting.dto;

import com.bridgework.posting.entity.PostingFeedbackReaction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PostingFeedbackCreateRequestDto(
        @NotNull(message = "reaction은 필수입니다.")
        PostingFeedbackReaction reaction,
        @NotBlank(message = "comment는 필수입니다.")
        @Size(max = 1000, message = "comment는 1000자 이하여야 합니다.")
        String comment
) {
}
