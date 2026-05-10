package com.bridgework.posting.dto;

public record PostingDetailDto(
        Long postingId,
        String externalId,
        String companyName,
        String jobTitle,
        String workAddress,
        String contactNumber,
        String employmentType,
        String enterType,
        String salaryType,
        String salary,
        String termDate,
        String offerRegisteredAt,
        String registeredAt,
        String requiredCareer,
        String requiredEducation,
        String requiredMajor,
        String requiredLicenses,
        String agencyName,
        Double geoLatitude,
        Double geoLongitude,
        String postingStatus,
        long scrapCount,
        boolean scrappedByMe
) {
}
