package com.bridgework.profile.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "근무 가능 시점 코드 (KO: 즉시/2주 이내/1개월 이내/협의 가능)")
public enum ProfileWorkAvailability implements LabeledEnum {
    IMMEDIATE("즉시"),
    WITHIN_TWO_WEEKS("2주 이내"),
    WITHIN_ONE_MONTH("1개월 이내"),
    NEGOTIABLE("협의 가능");

    private final String koreanLabel;

    ProfileWorkAvailability(String koreanLabel) {
        this.koreanLabel = koreanLabel;
    }

    @Override
    public String getKoreanLabel() {
        return koreanLabel;
    }
}
