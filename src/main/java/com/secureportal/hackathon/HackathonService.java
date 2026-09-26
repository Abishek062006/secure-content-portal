package com.secureportal.hackathon;

import com.secureportal.audit.AuditService;
import com.secureportal.gamification.GamificationService;
import com.secureportal.gamification.PointAction;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The hackathons admins list and the learners who register for them. Registering pays out its points exactly once. */
@Service
public class HackathonService {

    static final int MAX_LISTED = 200;
    static final int DEFAULT_POINTS = 25;
    static final int MAX_POINTS = 200;

    private final HackathonRepository hackathons;
    private final HackathonRegistrationRepository registrations;
    private final GamificationService gamification;
    private final AuditService audit;

    public HackathonService(HackathonRepository hackathons, HackathonRegistrationRepository registrations,
                            GamificationService gamification, AuditService audit) {
        this.hackathons = hackathons;
        this.registrations = registrations;
        this.gamification = gamification;
        this.audit = audit;
    }

    /** What an admin submits. Text is validated and tidied by the service; nothing here is trusted. */
    public record Input(String title, String organizer, String description, String bannerUrl, String stream, String mode,
                        String location, String prizePool, String registrationUrl, Instant registrationDeadline,
                        Instant eventStartDate, Instant eventEndDate, boolean featured, String status, Integer pointsReward) {
    }

    public record View(Hackathon hackathon, long participants, boolean registered) {
    }

    public record Registration(boolean alreadyRegistered, int pointsEarned, String registrationUrl) {
    }

    // ---- Reading ------------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<View> list(String stream, String mode, Long viewerId) {
        String streamFilter = stream == null || stream.isBlank() || stream.equalsIgnoreCase("all") ? null : stream.strip();
        String modeFilter = mode == null || mode.isBlank() || mode.equalsIgnoreCase("all") ? null
                : HackathonMode.parse(mode).map(Enum::name).orElse("?");
        List<Hackathon> found = hackathons.search(streamFilter, modeFilter, PageRequest.of(0, MAX_LISTED));
        return views(found, viewerId);
    }

    private List<View> views(List<Hackathon> found, Long viewerId) {
        if (found.isEmpty()) {
            return List.of();
        }
        List<Long> ids = found.stream().map(Hackathon::getId).toList();
        Map<Long, Long> counts = new HashMap<>();
        registrations.countByHackathons(ids).forEach(r -> counts.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue()));
        Set<Long> mine = viewerId == null ? Set.of() : new HashSet<>(registrations.registeredAmong(viewerId, ids));
        return found.stream().map(h -> new View(h, counts.getOrDefault(h.getId(), 0L), mine.contains(h.getId()))).toList();
    }

    // ---- Registering --------------------------------------------------------------------------------------------------

    @Transactional
    public Registration register(Long userId, Long hackathonId) {
        Hackathon hackathon = hackathons.findById(hackathonId).orElseThrow(HackathonNotFoundException::new);
        if (!hackathon.acceptsRegistrations(Instant.now())) {
            throw new HackathonClosedException();
        }
        int points = hackathon.getPointsReward();
        if (registrations.registerOnce(hackathonId, userId, points) == 0) {
            return new Registration(true, 0, hackathon.getRegistrationUrl());
        }
        boolean paid = points > 0 && gamification.award(userId, PointAction.HACKATHON_REGISTER, points,
                "Registered for hackathon: " + hackathon.getTitle(), hackathon.getStream(), hackathonId.toString());
        return new Registration(false, paid ? points : 0, hackathon.getRegistrationUrl());
    }

    // ---- Admin --------------------------------------------------------------------------------------------------------

    @Transactional
    public View create(Input input, String adminEmail) {
        Hackathon created = hackathons.save(new Hackathon(validate(input)));
        audit.log(adminEmail, "HACKATHON_CREATE", null, "Listed hackathon \"" + created.getTitle() + "\"");
        return new View(created, 0, false);
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
        int points = in.pointsReward() == null ? DEFAULT_POINTS : in.pointsReward();
        if (points < 0 || points > MAX_POINTS) {
            throw new InvalidHackathonException("The XP reward must be between 0 and " + MAX_POINTS + ".");
        }
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
                in.featured(), status, points);
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
