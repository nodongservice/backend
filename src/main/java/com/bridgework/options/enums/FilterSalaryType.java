package com.bridgework.options.enums;

public enum FilterSalaryType {
    MONTHLY_SALARY("월급"),
    ANNUAL_SALARY("연봉"),
    HOURLY_WAGE("시급"),
    DAILY_WAGE("일급"),
    PERFORMANCE_BASED("건별/성과급"),
    COMPANY_POLICY("회사 내규에 따름"),
    NEGOTIABLE_AFTER_INTERVIEW("면접 후 협의");

    private final String koreanLabel;

    FilterSalaryType(String koreanLabel) {
        this.koreanLabel = koreanLabel;
    }

    public String getKoreanLabel() {
        return koreanLabel;
    }
}
