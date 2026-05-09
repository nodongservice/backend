package com.bridgework.profile.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "고용형태 코드 (KO: 정규직/계약직/무기계약직/시간제/일용직/인턴/파견용역/재택원격)")
public enum ProfileWorkType implements LabeledEnum {
    FULL_TIME("정규직"),
    CONTRACT("계약직"),
    INDEFINITE_CONTRACT("무기계약직"),
    PART_TIME("시간제"),
    DAILY("일용직"),
    INTERN("인턴"),
    DISPATCH_OUTSOURCING("파견/용역"),
    REMOTE("재택/원격");

    private final String koreanLabel;

    ProfileWorkType(String koreanLabel) {
        this.koreanLabel = koreanLabel;
    }

    @Override
    public String getKoreanLabel() {
        return koreanLabel;
    }
}
