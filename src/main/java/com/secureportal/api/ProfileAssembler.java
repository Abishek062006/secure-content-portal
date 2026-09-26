package com.secureportal.api;

import com.secureportal.api.dto.CertificateDto;
import com.secureportal.api.dto.ProfileDto;
import com.secureportal.api.dto.ProfileDto.EntryDto;
import com.secureportal.certificate.CertificateService;
import com.secureportal.feed.PostRepository;
import com.secureportal.profile.EntryKind;
import com.secureportal.profile.Profile;
import com.secureportal.profile.ProfileEntry;
import com.secureportal.profile.ProfileService;
import com.secureportal.user.User;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class ProfileAssembler {

    private final ProfileService profileService;
    private final CertificateService certificateService;
    private final PostRepository postRepository;

    public ProfileAssembler(ProfileService profileService, CertificateService certificateService, PostRepository postRepository) {
        this.profileService = profileService;
        this.certificateService = certificateService;
        this.postRepository = postRepository;
    }

    /** The picture shown for a member: the one they uploaded, else the one from their Google account. */
    public static String avatarUrl(User user, Profile profile) {
        if (profile != null && profile.getAvatarKey() != null) {
            return "/api/profiles/" + user.getId() + "/avatar?v=" + profile.getUpdatedAt().toEpochMilli();
        }
        return user.getPictureUrl();
    }

    public ProfileDto profile(User user, Long viewerId) {
        Profile profile = profileService.get(user.getId());
        List<ProfileEntry> entries = profileService.entries(user.getId());
        return new ProfileDto(user.getId(), user.getDisplayName(), profile.getHeadline(), profile.getAbout(),
                profile.getLocation(), profile.getWebsite(), avatarUrl(user, profile),
                profile.getBannerKey() == null ? null
                        : "/api/profiles/" + user.getId() + "/banner?v=" + profile.getUpdatedAt().toEpochMilli(),
                user.getId().equals(viewerId), profile.getAvatarKey() != null,
                of(entries, EntryKind.EDUCATION), of(entries, EntryKind.EXPERIENCE), of(entries, EntryKind.SKILL),
                certificateService.forUser(user.getId()).stream()
                        .sorted((a, b) -> b.getIssuedAt().compareTo(a.getIssuedAt())).map(CertificateDto::of).toList(),
                postRepository.countByAuthorIdAndPublishAtBefore(user.getId(), Instant.now()));
    }

    public EntryDto entry(ProfileEntry e) {
        return new EntryDto(e.getId(), e.getTitle(), e.getSubtitle(), e.getStartYear(), e.getEndYear(), e.getDescription());
    }

    private List<EntryDto> of(List<ProfileEntry> entries, EntryKind kind) {
        return entries.stream().filter(e -> e.getKind() == kind).map(this::entry).toList();
    }
}
