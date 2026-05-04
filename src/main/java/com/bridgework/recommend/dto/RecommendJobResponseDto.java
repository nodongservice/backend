package com.bridgework.recommend.dto;

public record RecommendJobResponseDto(
        String externalId,
        String busplaName,
        String jobNm,
        String compAddr,
        String empType,
        String enterType,
        String salaryType,
        String salary,
        String termDate,
        String offerregDt,
        String regDt,
        String reqCareer,
        String reqEduc,
        String reqMajor,
        String reqLicens,
        Double geoLatitude,
        Double geoLongitude
) {
}

