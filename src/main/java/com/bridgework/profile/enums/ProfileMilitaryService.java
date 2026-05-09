package com.bridgework.profile.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "병역 상태 코드 (KO: 군필/면제/해당없음/복무중)")
public enum ProfileMilitaryService implements LabeledEnum {
    COMPLETED("군필"),
    EXEMPTED("면제"),
    NOT_APPLICABLE("해당없음"),
    SERVING("복무중");

    private final String koreanLabel;

    ProfileMilitaryService(String koreanLabel) {
        this.koreanLabel = koreanLabel;
    }

    @Override
    public String getKoreanLabel() {
        return koreanLabel;
    }
}
