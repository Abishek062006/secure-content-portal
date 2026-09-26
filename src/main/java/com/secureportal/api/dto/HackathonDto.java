package com.secureportal.api.dto;

import com.secureportal.hackathon.Hackathon;
import com.secureportal.hackathon.HackathonService;

import java.time.Instant;

public record HackathonDto(Long id, String title, String organizer, String description, String bannerUrl, String stream, String mode,
                           String location, String prizePool, String registrationUrl, Instant registrationDeadline,
                           Instant eventStartDate, Instant eventEndDate, boolean featured, String status, int pointsReward,
                           long participantCount, boolean isRegistered) {

    public static HackathonDto of(HackathonService.View view) {
        Hackathon h = view.hackathon();
        return new HackathonDto(h.getId(), h.getTitle(), h.getOrganizer(), h.getDescription(), h.getBannerUrl(), h.getStream(),
                h.getMode(), h.getLocation(), h.getPrizePool(), h.getRegistrationUrl(), h.getRegistrationDeadline(),
                h.getEventStartDate(), h.getEventEndDate(), h.isFeatured(), h.getStatus(), h.getPointsReward(),
                view.participants(), view.registered());
    }
}
