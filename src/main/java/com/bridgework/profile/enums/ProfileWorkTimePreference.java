package com.bridgework.profile.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "시간 선호 코드 (KO: 주간/오전/오후/야간/탄력근무/협의 가능)")
public enum ProfileWorkTimePreference implements LabeledEnum {
    DAYTIME("주간"),
    MORNING("오전"),
    AFTERNOON("오후"),
    EVENING("야간"),
    FLEXIBLE("탄력근무"),
    NEGOTIABLE("협의 가능");

    private final String koreanLabel;

    ProfileWorkTimePreference(String koreanLabel) {
        this.koreanLabel = koreanLabel;
    }

    @Override
    public String getKoreanLabel() {
        return koreanLabel;
    }
}
