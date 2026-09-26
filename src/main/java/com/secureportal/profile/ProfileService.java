package com.secureportal.profile;

import com.secureportal.common.FileValidator;
import com.secureportal.common.ValidatedFile;
import com.secureportal.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Year;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Everything a member can say about themselves: headline, about, images, education, experience and skills. */
@Service
public class ProfileService {

    static final int MAX_ENTRIES_PER_KIND = 40;

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final ProfileRepository profileRepository;
    private final ProfileEntryRepository entryRepository;
    private final FileValidator fileValidator;
    private final StorageService storageService;

    public ProfileService(ProfileRepository profileRepository, ProfileEntryRepository entryRepository,
                          FileValidator fileValidator, StorageService storageService) {
        this.profileRepository = profileRepository;
        this.entryRepository = entryRepository;
        this.fileValidator = fileValidator;
        this.storageService = storageService;
    }

    /** A member who has never edited their profile simply has an empty one. */
    public Profile get(Long userId) {
        return profileRepository.findById(userId).orElseGet(() -> new Profile(userId));
    }

    /** Profiles with an uploaded picture, for showing avatars next to posts. */
    public Map<Long, Profile> profiles(Collection<Long> userIds) {
        Map<Long, Profile> map = new HashMap<>();
        profileRepository.findAllById(userIds).forEach(p -> map.put(p.getUserId(), p));
        return map;
    }

    public List<ProfileEntry> entries(Long userId) {
        return entryRepository.findByUserId(userId).stream()
                .sorted(Comparator.comparing((ProfileEntry e) -> e.getEndYear() == null ? Integer.MAX_VALUE : e.getEndYear()).reversed()
                        .thenComparing(e -> e.getStartYear() == null ? 0 : e.getStartYear(), Comparator.reverseOrder()))
                .toList();
    }

    @Transactional
    public Profile update(Long userId, String headline, String about, String location, String website) {
        Profile profile = profileRepository.findById(userId).orElseGet(() -> new Profile(userId));
        profile.setDetails(clean(headline, 220, "The headline"), clean(about, 2600, "About"),
                clean(location, 120, "The location"), cleanWebsite(website));
        return profileRepository.save(profile);
    }

    @Transactional
    public Profile setImage(Long userId, boolean banner, MultipartFile file) {
        ValidatedFile validated = fileValidator.validateThumbnail(file);
        Profile profile = profileRepository.findById(userId).orElseGet(() -> new Profile(userId));
        String previous = banner ? profile.getBannerKey() : profile.getAvatarKey();
        String key = "profiles/" + userId + "/" + (banner ? "banner" : "avatar") + "/" + UUID.randomUUID() + "-"
                + validated.originalFilename().replaceAll("[^a-zA-Z0-9._-]", "_");
        try (InputStream in = file.getInputStream()) {
            storageService.put(key, in, validated.sizeBytes(), validated.detectedMimeType());
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the uploaded image", e);
        }
        if (banner) {
            profile.setBanner(key, validated.detectedMimeType());
        } else {
            profile.setAvatar(key, validated.detectedMimeType());
        }
        try {
            Profile saved = profileRepository.save(profile);
            deleteQuietly(previous);
            return saved;
        } catch (RuntimeException e) {
            deleteQuietly(key);
            throw e;
        }
    }

    @Transactional
    public Profile removeImage(Long userId, boolean banner) {
        Profile profile = profileRepository.findById(userId).orElseGet(() -> new Profile(userId));
        String previous = banner ? profile.getBannerKey() : profile.getAvatarKey();
        if (banner) {
            profile.setBanner(null, null);
        } else {
            profile.setAvatar(null, null);
        }
        Profile saved = profileRepository.save(profile);
        deleteQuietly(previous);
        return saved;
    }

    @Transactional
    public ProfileEntry addEntry(Long userId, EntryKind kind, String title, String subtitle, Integer startYear,
                                 Integer endYear, String description) {
        if (entryRepository.countByUserIdAndKind(userId, kind) >= MAX_ENTRIES_PER_KIND) {
            throw new InvalidProfileException("You can add up to " + MAX_ENTRIES_PER_KIND + " of these.");
        }
        ProfileEntry entry = new ProfileEntry(userId, kind);
        apply(entry, title, subtitle, startYear, endYear, description);
        return entryRepository.save(entry);
    }

    @Transactional
    public ProfileEntry updateEntry(Long userId, UUID entryId, String title, String subtitle, Integer startYear,
                                    Integer endYear, String description) {
        ProfileEntry entry = own(userId, entryId);
        apply(entry, title, subtitle, startYear, endYear, description);
        return entryRepository.save(entry);
    }

    @Transactional
    public void deleteEntry(Long userId, UUID entryId) {
        entryRepository.delete(own(userId, entryId));
    }

    private ProfileEntry own(Long userId, UUID entryId) {
        return entryRepository.findById(entryId).filter(e -> e.getUserId().equals(userId))
                .orElseThrow(ProfileNotFoundException::new);
    }

    private void apply(ProfileEntry entry, String title, String subtitle, Integer startYear, Integer endYear, String description) {
        String name = clean(title, 200, "The title");
        if (name == null) {
            throw new InvalidProfileException("Enter a "
                    + (entry.getKind() == EntryKind.EDUCATION ? "school" : entry.getKind() == EntryKind.SKILL ? "skill" : "company") + ".");
        }
        if (entry.getKind() == EntryKind.SKILL) {
            entry.set(name, null, null, null, null);
            return;
        }
        int maxYear = Year.now().getValue() + 8;
        for (Integer year : new Integer[]{startYear, endYear}) {
            if (year != null && (year < 1950 || year > maxYear)) {
                throw new InvalidProfileException("Years must be between 1950 and " + maxYear + ".");
            }
        }
        if (startYear != null && endYear != null && endYear < startYear) {
            throw new InvalidProfileException("The end year can't be before the start year.");
        }
        entry.set(name, clean(subtitle, 200, "The degree or role"), startYear, endYear, clean(description, 1500, "The description"));
    }

    private static String clean(String value, int max, String label) {
        String text = value == null ? "" : value.trim();
        if (text.length() > max) {
            throw new InvalidProfileException(label + " must be " + max + " characters or fewer.");
        }
        return text.isEmpty() ? null : text;
    }

    /** Only web links: a "javascript:" address in a profile would run when someone clicks it. */
    private static String cleanWebsite(String value) {
        String text = clean(value, 300, "The website");
        if (text == null) {
            return null;
        }
        String lower = text.toLowerCase();
        // "javascript:...", "ftp://...", "mailto:..." are other schemes, not a website; "host:8080" is fine.
        if (!lower.startsWith("http://") && !lower.startsWith("https://")
                && lower.matches("^[a-z][a-z0-9+.-]*:(//|[^0-9/]).*")) {
            throw new InvalidProfileException("Enter a valid website address.");
        }
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            text = "https://" + text;
        }
        try {
            java.net.URI uri = java.net.URI.create(text);
            if (uri.getHost() == null || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new InvalidProfileException("Enter a valid website address.");
        }
        return text;
    }

    private void deleteQuietly(String key) {
        if (key == null) {
            return;
        }
        try {
            storageService.delete(key);
        } catch (RuntimeException e) {
            log.warn("Could not delete storage object {}", key, e);
        }
    }
}
