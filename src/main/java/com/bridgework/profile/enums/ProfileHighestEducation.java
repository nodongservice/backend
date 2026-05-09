package com.bridgework.profile.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "최종 학력 코드 (KO: 고졸 이하/고졸/전문대졸/대졸/석사/박사/기타)")
public enum ProfileHighestEducation implements LabeledEnum {
    HIGH_SCHOOL_OR_BELOW("고졸 이하"),
    HIGH_SCHOOL("고졸"),
    COLLEGE("전문대졸"),
    BACHELOR("대졸"),
    MASTER("석사"),
    DOCTOR("박사"),
    OTHER("기타");

    private final String koreanLabel;

    ProfileHighestEducation(String koreanLabel) {
        this.koreanLabel = koreanLabel;
    }

    @Override
    public String getKoreanLabel() {
        return koreanLabel;
    }
}
