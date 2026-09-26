package com.secureportal.api.dto;

import java.util.List;
import java.util.UUID;

/** A member's profile as anyone signed in sees it. {@code avatarUrl} falls back to their Google picture. */
public record ProfileDto(
        Long userId,
        String name,
        String headline,
        String about,
        String location,
        String website,
        String avatarUrl,
        String bannerUrl,
        boolean mine,
        boolean hasUploadedAvatar,
        List<EntryDto> education,
        List<EntryDto> experience,
        List<EntryDto> skills,
        List<CertificateDto> certificates,
        long postCount
) {
    public record EntryDto(UUID id, String title, String subtitle, Integer startYear, Integer endYear, String description) {
    }
}
