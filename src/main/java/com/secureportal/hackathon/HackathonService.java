package com.secureportal.hackathon;

import com.secureportal.audit.AuditService;
import com.secureportal.common.FileValidator;
import com.secureportal.common.ValidatedFile;
import com.secureportal.storage.StorageService;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The external hackathons admins list, and the ones learners save. Learners register on the organiser's own site. */
@Service
public class HackathonService {

    static final int MAX_LISTED = 200;
    static final int MAX_TEAM = 10;

    private final HackathonRepository hackathons;
    private final HackathonSaveRepository saves;
    private final AuditService audit;
    private final FileValidator fileValidator;
    private final StorageService storage;

    public HackathonService(HackathonRepository hackathons, HackathonSaveRepository saves, AuditService audit,
                            FileValidator fileValidator, StorageService storage) {
        this.hackathons = hackathons;
        this.saves = saves;
        this.audit = audit;
        this.fileValidator = fileValidator;
        this.storage = storage;
    }

    /** What an admin submits. Text is validated and tidied by the service; nothing here is trusted. */
    public record Input(String title, String organizer, String description, String bannerUrl, String stream, String mode,
                        String location, String prizePool, String registrationUrl, Instant registrationDeadline,
                        Instant eventStartDate, Instant eventEndDate, boolean featured, String status, String kind, String rules,
                        String tracks, String prizes, Integer minTeamSize, Integer maxTeamSize) {
    }

    public record View(Hackathon hackathon, boolean saved, boolean registrationOpen) {
    }

    // ---- Reading ------------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<View> list(String stream, String mode, boolean onlySaved, Long viewerId) {
        if (onlySaved) {
            return viewerId == null ? List.of() : views(hackathons.savedBy(viewerId, PageRequest.of(0, MAX_LISTED)), viewerId);
        }
        String streamFilter = stream == null || stream.isBlank() || stream.equalsIgnoreCase("all") ? null : stream.strip();
        String modeFilter = mode == null || mode.isBlank() || mode.equalsIgnoreCase("all") ? null
                : HackathonMode.parse(mode).map(Enum::name).orElse("?");
        return views(hackathons.search(streamFilter, modeFilter, PageRequest.of(0, MAX_LISTED)), viewerId);
    }

    @Transactional(readOnly = true)
    public View view(Long id, Long viewerId) {
        return views(List.of(find(id)), viewerId).get(0);
    }

    @Transactional(readOnly = true)
    public Hackathon find(Long id) {
        return hackathons.findById(id).orElseThrow(HackathonNotFoundException::new);
    }

    private List<View> views(List<Hackathon> found, Long viewerId) {
        if (found.isEmpty()) {
            return List.of();
        }
        List<Long> ids = found.stream().map(Hackathon::getId).toList();
        Set<Long> mine = viewerId == null ? Set.of() : new HashSet<>(saves.savedAmong(viewerId, ids));
        Instant now = Instant.now();
        return found.stream().map(h -> new View(h, mine.contains(h.getId()), h.registrationOpen(now))).toList();
    }

    // ---- Saving -------------------------------------------------------------------------------------------------------

    /** Saving is idempotent: saving twice leaves one save. */
    @Transactional
    public void save(Long userId, Long hackathonId) {
        if (!hackathons.existsById(hackathonId)) {
            throw new HackathonNotFoundException();
        }
        saves.saveOnce(hackathonId, userId);
    }

    @Transactional
    public void unsave(Long userId, Long hackathonId) {
        saves.remove(hackathonId, userId);
    }

    // ---- Admin --------------------------------------------------------------------------------------------------------

    @Transactional
    public View create(Input input, String adminEmail) {
        Hackathon created = hackathons.save(new Hackathon(validate(input)));
        audit.log(adminEmail, "HACKATHON_CREATE", null, "Listed hackathon \"" + created.getTitle() + "\"");
        return new View(created, false, created.registrationOpen(Instant.now()));
    }

    @Transactional
    public View update(Long id, Input input, String adminEmail) {
        Hackathon hackathon = hackathons.findById(id).orElseThrow(HackathonNotFoundException::new);
        Hackathon.Details details = validate(input);
        if (hackathon.isHosted() != (details.hosted() != null)) {
            throw new InvalidHackathonException("An event can't change between an external listing and a hosted event.");
        }
        if (hackathon.isHosted() && hackathon.getResultsPublishedAt() != null) {
            throw new HackathonStateException("Results are published, so the event can no longer be edited.");
        }
        hackathon.apply(details);
        hackathons.save(hackathon);
        audit.log(adminEmail, "HACKATHON_UPDATE", null, "Updated hackathon \"" + hackathon.getTitle() + "\"");
        return views(List.of(hackathon), null).get(0);
    }

    /** Replaces the banner with an uploaded image (checked by its contents, not its name). The old file is removed afterwards. */
    @Transactional
    public View replaceBanner(Long id, MultipartFile file, String adminEmail) {
        Hackathon hackathon = hackathons.findById(id).orElseThrow(HackathonNotFoundException::new);
        ValidatedFile validated = fileValidator.validateThumbnail(file);
        String previous = hackathon.getBannerKey();
        String key = "hackathons/" + id + "/banner/" + java.util.UUID.randomUUID() + "-"
                + validated.originalFilename().replaceAll("[^a-zA-Z0-9._-]", "_");
        try (java.io.InputStream in = file.getInputStream()) {
            storage.put(key, in, validated.sizeBytes(), validated.detectedMimeType());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not read the uploaded banner", e);
        }
        hackathon.setBanner(key, validated.detectedMimeType());
        hackathons.save(hackathon);
        deleteQuietly(previous);
        audit.log(adminEmail, "HACKATHON_UPDATE", null, "Changed the banner of \"" + hackathon.getTitle() + "\"");
        return views(List.of(hackathon), null).get(0);
    }

    private void deleteQuietly(String key) {
        if (key == null) {
            return;
        }
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger(HackathonService.class).warn("Could not delete storage object {}", key, e);
        }
    }

    @Transactional
    public void delete(Long id, String adminEmail) {
        Hackathon hackathon = hackathons.findById(id).orElseThrow(HackathonNotFoundException::new);
        hackathons.delete(hackathon);
        deleteQuietly(hackathon.getBannerKey());
        audit.log(adminEmail, "HACKATHON_DELETE", null, "Removed hackathon \"" + hackathon.getTitle() + "\"");
    }

    // ---- Validation ---------------------------------------------------------------------------------------------------

    private static Hackathon.Details validate(Input in) {
        HackathonMode mode = HackathonMode.parse(in.mode())
                .orElseThrow(() -> new InvalidHackathonException("Choose Online, Offline or Hybrid for the mode."));
        HackathonStatus status = HackathonStatus.parse(in.status())
                .orElseThrow(() -> new InvalidHackathonException("Choose Upcoming, Active or Completed for the status."));
        if (in.eventStartDate() != null && in.eventEndDate() != null && in.eventEndDate().isBefore(in.eventStartDate())) {
            throw new InvalidHackathonException("The event can't end before it starts.");
        }
        HackathonKind kind = in.kind() == null || in.kind().isBlank() ? HackathonKind.EXTERNAL
                : HackathonKind.parse(in.kind()).orElseThrow(() -> new InvalidHackathonException("Choose External listing or Hosted event."));
        Hackathon.Hosted hosted = kind == HackathonKind.HOSTED ? hosted(in) : null;
        return new Hackathon.Details(
                required(in.title(), 200, "The title"),
                optional(in.organizer(), 200, "The organizer"),
                optional(in.description(), 5000, "The description"),
                optionalHttpsUrl(in.bannerUrl(), 500, "The banner image address"),
                required(in.stream(), 100, "The stream"),
                mode,
                optional(in.location(), 200, "The location"),
                optional(in.prizePool(), 100, "The prize pool"),
                hosted == null ? requiredHttpsUrl(in.registrationUrl(), 1000, "The registration link")
                        : optionalHttpsUrl(in.registrationUrl(), 1000, "The registration link"),
                in.registrationDeadline(), in.eventStartDate(), in.eventEndDate(),
                in.featured(), status, hosted);
    }

    /** A hosted event needs its whole timeline in order, and sensible team sizes. */
    private static Hackathon.Hosted hosted(Input in) {
        if (in.registrationDeadline() == null || in.eventStartDate() == null || in.eventEndDate() == null) {
            throw new InvalidHackathonException("A hosted event needs a registration deadline, a start and an end (the submission deadline).");
        }
        if (in.registrationDeadline().isAfter(in.eventStartDate())) {
            throw new InvalidHackathonException("Registration must close before the event starts.");
        }
        if (!in.eventEndDate().isAfter(in.eventStartDate())) {
            throw new InvalidHackathonException("The submission deadline must be after the event starts.");
        }
        int min = in.minTeamSize() == null ? 1 : in.minTeamSize();
        int max = in.maxTeamSize() == null ? 4 : in.maxTeamSize();
        if (min < 1 || max > MAX_TEAM || min > max) {
            throw new InvalidHackathonException("Team size must be between 1 and " + MAX_TEAM + ", and the minimum can't exceed the maximum.");
        }
        return new Hackathon.Hosted(required(in.rules(), 5000, "The rules"), tracks(in.tracks()), optional(in.prizes(), 2000, "The prizes"), min, max);
    }

    /** Tracks are a short comma-separated list of names. */
    private static String tracks(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        List<String> names = java.util.Arrays.stream(raw.split(",")).map(String::strip).filter(t -> !t.isEmpty()).distinct().toList();
        if (names.size() > 8 || names.stream().anyMatch(t -> t.length() > 60)) {
            throw new InvalidHackathonException("Give at most 8 tracks, each under 60 characters.");
        }
        return names.isEmpty() ? null : String.join(", ", names);
    }

    private static String required(String value, int max, String label) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty()) {
            throw new InvalidHackathonException(label + " is required.");
        }
        if (text.length() > max) {
            throw new InvalidHackathonException(label + " can be at most " + max + " characters.");
        }
        return text;
    }

    private static String optional(String value, int max, String label) {
        return value == null || value.isBlank() ? null : required(value, max, label);
    }

    private static String optionalHttpsUrl(String value, int max, String label) {
        return value == null || value.isBlank() ? null : requiredHttpsUrl(value, max, label);
    }

    /** Only https links to a real host. That rules out javascript:, data:, file: and plain-http addresses being handed to learners. */
    private static String requiredHttpsUrl(String value, int max, String label) {
        String text = required(value, max, label);
        try {
            URI uri = new URI(text);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()
                    || uri.getUserInfo() != null || text.chars().anyMatch(Character::isWhitespace)) {
                throw new InvalidHackathonException(label + " must be a full https:// link.");
            }
        } catch (URISyntaxException e) {
            throw new InvalidHackathonException(label + " must be a full https:// link.");
        }
        return text;
    }
}
