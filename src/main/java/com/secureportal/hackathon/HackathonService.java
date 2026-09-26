package com.secureportal.hackathon;

import com.secureportal.audit.AuditService;
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

    private final HackathonRepository hackathons;
    private final HackathonSaveRepository saves;
    private final AuditService audit;

    public HackathonService(HackathonRepository hackathons, HackathonSaveRepository saves, AuditService audit) {
        this.hackathons = hackathons;
        this.saves = saves;
        this.audit = audit;
    }

    /** What an admin submits. Text is validated and tidied by the service; nothing here is trusted. */
    public record Input(String title, String organizer, String description, String bannerUrl, String stream, String mode,
                        String location, String prizePool, String registrationUrl, Instant registrationDeadline,
                        Instant eventStartDate, Instant eventEndDate, boolean featured, String status) {
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
        hackathon.apply(validate(input));
        hackathons.save(hackathon);
        audit.log(adminEmail, "HACKATHON_UPDATE", null, "Updated hackathon \"" + hackathon.getTitle() + "\"");
        return views(List.of(hackathon), null).get(0);
    }

    @Transactional
    public void delete(Long id, String adminEmail) {
        Hackathon hackathon = hackathons.findById(id).orElseThrow(HackathonNotFoundException::new);
        hackathons.delete(hackathon);
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
        return new Hackathon.Details(
                required(in.title(), 200, "The title"),
                optional(in.organizer(), 200, "The organizer"),
                optional(in.description(), 5000, "The description"),
                optionalHttpsUrl(in.bannerUrl(), 500, "The banner image address"),
                required(in.stream(), 100, "The stream"),
                mode,
                optional(in.location(), 200, "The location"),
                optional(in.prizePool(), 100, "The prize pool"),
                requiredHttpsUrl(in.registrationUrl(), 1000, "The registration link"),
                in.registrationDeadline(), in.eventStartDate(), in.eventEndDate(),
                in.featured(), status);
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
