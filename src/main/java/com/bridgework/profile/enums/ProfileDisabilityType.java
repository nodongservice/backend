package com.bridgework.profile.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "장애 유형 코드")
public enum ProfileDisabilityType implements LabeledEnum {
    PHYSICAL("지체장애"),
    BRAIN_LESION("뇌병변장애"),
    VISUAL("시각장애"),
    HEARING("청각장애"),
    SPEECH("언어장애"),
    INTELLECTUAL("지적장애"),
    AUTISM("자폐성장애"),
    MENTAL("정신장애"),
    KIDNEY("신장장애"),
    HEART("심장장애"),
    RESPIRATORY("호흡기장애"),
    LIVER("간장애"),
    FACE("안면장애"),
    STOMA_URINARY("장루·요루장애"),
    EPILEPSY("뇌전증장애"),
    OTHER("기타");

    private final String koreanLabel;

    ProfileDisabilityType(String koreanLabel) {
        this.koreanLabel = koreanLabel;
    }

    @Override
    public String getKoreanLabel() {
        return koreanLabel;
    }
}
