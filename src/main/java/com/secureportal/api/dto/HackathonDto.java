package com.secureportal.api.dto;

import com.secureportal.hackathon.Hackathon;
import com.secureportal.hackathon.HackathonService;

import java.time.Instant;

public record HackathonDto(Long id, String title, String organizer, String description, String bannerUrl, String stream, String mode,
                           String location, String prizePool, String registrationUrl, Instant registrationDeadline,
                           Instant eventStartDate, Instant eventEndDate, boolean featured, String status, boolean saved,
                           boolean registrationOpen, String kind, String phase, String rules, String tracks, String prizes,
                           int minTeamSize, int maxTeamSize, boolean resultsPublished) {

    public static HackathonDto of(HackathonService.View view) {
        Hackathon h = view.hackathon();
        return new HackathonDto(h.getId(), h.getTitle(), h.getOrganizer(), h.getDescription(),
                h.getBannerKey() != null ? "/api/hackathons/" + h.getId() + "/banner" : h.getBannerUrl(), h.getStream(),
                h.getMode(), h.getLocation(), h.getPrizePool(), h.getRegistrationUrl(), h.getRegistrationDeadline(),
                h.getEventStartDate(), h.getEventEndDate(), h.isFeatured(), h.getStatus(), view.saved(), view.registrationOpen(),
                h.getKind(), h.phase(java.time.Instant.now()) == null ? null : h.phase(java.time.Instant.now()).name(), h.getRules(), h.getTracks(),
                h.getPrizes(), h.getMinTeamSize(), h.getMaxTeamSize(), h.getResultsPublishedAt() != null);
    }
}
