package com.bridgework.profile.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "졸업 상태 코드 (KO: 졸업/졸업예정/재학/수료/중퇴/기타)")
public enum ProfileGraduationStatus implements LabeledEnum {
    GRADUATED("졸업"),
    EXPECTED("졸업예정"),
    ENROLLED("재학"),
    COMPLETED("수료"),
    DROPPED_OUT("중퇴"),
    OTHER("기타");

    private final String koreanLabel;

    ProfileGraduationStatus(String koreanLabel) {
        this.koreanLabel = koreanLabel;
    }

    @Override
    public String getKoreanLabel() {
        return koreanLabel;
    }
}
