package com.bridgework.profile.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "장애 정도 코드 (KO: 중증/중등도/경증)")
public enum ProfileDisabilitySeverity implements LabeledEnum {
    SEVERE("중증"),
    MODERATE("중등도"),
    MILD("경증");

    private final String koreanLabel;

    ProfileDisabilitySeverity(String koreanLabel) {
        this.koreanLabel = koreanLabel;
    }

    @Override
    public String getKoreanLabel() {
        return koreanLabel;
    }
}
