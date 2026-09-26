package com.secureportal.gamification;

import com.secureportal.audit.AuditService;
import com.secureportal.user.User;
import com.secureportal.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Points, streaks and badges. Every award is idempotent: it is tied to what earned it (a lesson, a quiz, a course, a day, a
 * hackathon, an interview) and the database refuses a second one, so retaking a quiz, re-completing a lesson or firing the same
 * request twice can never pay out twice. Points only ever change through atomic SQL, never by read-modify-write. The hooks the
 * learning flows call run in their own transaction, so a failure here can never undo a learner's progress or quiz result.
 */
@Service
public class GamificationService {

    private static final Logger log = LoggerFactory.getLogger(GamificationService.class);

    static final int MAX_ADJUSTMENT = 10_000;
    static final int MAX_RULE_POINTS = 1_000;
    static final int MIN_REASON = 3;
    static final int MAX_REASON = 200;

    private static final Pattern WEB_STREAM = Pattern.compile("\\b(web|engineering|code|coding)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern AI_STREAM = Pattern.compile("\\b(ai|ml|data)\\b", Pattern.CASE_INSENSITIVE);

    private final UserGamificationRepository gamification;
    private final BadgeRepository badges;
    private final UserBadgeRepository userBadges;
    private final PointTransactionRepository ledger;
    private final PointRuleRepository rules;
    private final UserRepository users;
    private final AuditService audit;

    public GamificationService(UserGamificationRepository gamification, BadgeRepository badges, UserBadgeRepository userBadges,
                               PointTransactionRepository ledger, PointRuleRepository rules, UserRepository users,
                               AuditService audit) {
        this.gamification = gamification;
        this.badges = badges;
        this.userBadges = userBadges;
        this.ledger = ledger;
        this.rules = rules;
        this.users = users;
        this.audit = audit;
    }

    // ---- Reading ------------------------------------------------------------------------------------------------------

    public record Summary(int totalPoints, int currentStreak, int maxStreak, boolean checkedInToday, long unlockedBadges) {
    }

    /** A learner's own numbers. Reading never creates a row; someone with no activity yet is simply all zeros. */
    @Transactional(readOnly = true)
    public Summary summary(Long userId) {
        UserGamification mine = gamification.findById(userId).orElse(null);
        long unlocked = userBadges.countByUserId(userId);
        if (mine == null) {
            return new Summary(0, 0, 0, false, unlocked);
        }
        boolean today = today().equals(mine.getLastCheckinDate());
        return new Summary(mine.getTotalPoints(), mine.getCurrentStreak(), mine.getMaxStreak(), today, unlocked);
    }

    public record BadgeStatus(String id, String title, String description, String category, String icon, int pointsReward,
                              String rarity, boolean unlocked, Instant unlockedAt) implements Serializable {
    }

    @Transactional(readOnly = true)
    public List<BadgeStatus> badgesOf(Long userId) {
        Map<String, Instant> unlockedAt = new HashMap<>();
        userBadges.findByUserId(userId).forEach(ub -> unlockedAt.put(ub.getBadgeId(), ub.getUnlockedAt()));
        return badges.findAll().stream()
                .map(b -> new BadgeStatus(b.getId(), b.getTitle(), b.getDescription(), b.getCategory(), b.getIcon(),
                        b.getPointsReward(), b.getRarity(), unlockedAt.containsKey(b.getId()), unlockedAt.get(b.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PointTransaction> historyOf(Long userId, int limit) {
        return ledger.findByUserIdOrderByCreatedAtDescIdDesc(userId, org.springframework.data.domain.PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public List<PointTransaction> history(Long userId, String actionType, String stream, int limit) {
        return ledger.filter(userId, blankToNull(actionType), blankToNull(stream), org.springframework.data.domain.PageRequest.of(0, limit));
    }

    // ---- Earning ------------------------------------------------------------------------------------------------------

    public record CheckInResult(int pointsEarned, int newStreak, boolean claimedToday, List<Badge> unlockedBadges) {
    }

    /** One check-in per learner per UTC day. The learner's row is locked, so a double click can't be paid twice. */
    @Transactional
    public CheckInResult checkIn(Long userId) {
        gamification.ensureRow(userId);
        UserGamification mine = gamification.findForUpdate(userId).orElseThrow();
        LocalDate today = today();

        if (today.equals(mine.getLastCheckinDate())) {
            return new CheckInResult(0, mine.getCurrentStreak(), true, List.of());
        }

        int streak = 1;
        if (mine.getLastCheckinDate() != null && ChronoUnit.DAYS.between(mine.getLastCheckinDate(), today) == 1) {
            streak = mine.getCurrentStreak() + 1;
        }
        mine.setCurrentStreak(streak);
        mine.setLastCheckinDate(today);
        gamification.save(mine);

        int bonus = streak % 7 == 0 ? 25 : streak % 3 == 0 ? 10 : 0;
        int earned = pointsFor(PointAction.DAILY_CHECKIN) + bonus;
        awardOnce(userId, PointAction.DAILY_CHECKIN, earned, "Daily Check-in (" + streak + " day streak)", null,
                "DAILY_CHECKIN", today.toString());

        List<Badge> unlocked = new ArrayList<>();
        if (streak >= 3) unlockBadge(userId, "STREAK_MASTER").ifPresent(unlocked::add);
        if (streak >= 7) unlockBadge(userId, "STREAK_7").ifPresent(unlocked::add);
        if (streak >= 30) unlockBadge(userId, "STREAK_30").ifPresent(unlocked::add);
        return new CheckInResult(earned, streak, false, unlocked);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Badge> recordLessonCompletion(Long userId, String lessonId, String stream) {
        List<Badge> unlocked = new ArrayList<>();
        if (awardOnce(userId, PointAction.LESSON_COMPLETED, pointsFor(PointAction.LESSON_COMPLETED),
                "Completed a video lesson", stream, "LESSON", lessonId)) {
            unlockBadge(userId, "FAST_LEARNER").ifPresent(unlocked::add);
        }
        return unlocked;
    }

    /** Points are for passing a quiz or assessment the first time; retakes never pay again. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Badge> recordQuizAttempt(Long userId, String assessmentId, int score, boolean passed, String stream) {
        List<Badge> unlocked = new ArrayList<>();
        if (passed) {
            awardOnce(userId, PointAction.QUIZ_PASSED, pointsFor(PointAction.QUIZ_PASSED), "Passed quiz with " + score + "% score",
                    stream, "QUIZ", assessmentId);
            if (ledger.countByUserIdAndActionType(userId, PointAction.QUIZ_PASSED.name()) >= 5) {
                unlockBadge(userId, "QUIZ_MASTER").ifPresent(unlocked::add);
            }
            Instant dayAgo = Instant.now().minus(24, ChronoUnit.HOURS);
            if (ledger.countByUserIdAndActionTypeAndCreatedAtAfter(userId, PointAction.QUIZ_PASSED.name(), dayAgo) >= 3) {
                unlockBadge(userId, "SPEED_DEMON").ifPresent(unlocked::add);
            }
        }
        if (score == 100) {
            unlockBadge(userId, "QUIZ_ACE").ifPresent(unlocked::add);
        }
        return unlocked;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Badge> recordCourseCompletion(Long userId, String courseId, String stream) {
        List<Badge> unlocked = new ArrayList<>();
        if (!awardOnce(userId, PointAction.COURSE_COMPLETED, pointsFor(PointAction.COURSE_COMPLETED), "Completed full course",
                stream, "COURSE", courseId)) {
            return unlocked;
        }
        unlockBadge(userId, "COURSE_GRADUATE").ifPresent(unlocked::add);
        unlockBadge(userId, "COURSE_STARTER").ifPresent(unlocked::add);

        if (stream != null) {
            if (ledger.countByUserIdAndActionTypeAndStream(userId, PointAction.COURSE_COMPLETED.name(), stream) >= 2) {
                unlockBadge(userId, "STREAM_SPECIALIST").ifPresent(unlocked::add);
            }
            if (WEB_STREAM.matcher(stream).find()) {
                unlockBadge(userId, "WEB_DEV_PIONEER").ifPresent(unlocked::add);
            } else if (AI_STREAM.matcher(stream).find()) {
                unlockBadge(userId, "AI_EXPLORER").ifPresent(unlocked::add);
            }
        }
        return unlocked;
    }

    /**
     * Awards points for something that can only be rewarded once ({@code sourceId} says which thing). Returns whether this call
     * paid out: false means it already had, and nothing changed.
     */
    @Transactional
    public boolean award(Long userId, PointAction action, int amount, String description, String stream, String sourceId) {
        return awardOnce(userId, action, amount, description, stream, action.name(), sourceId);
    }

    public int pointsFor(PointAction action) {
        return rules.findByActionType(action.name()).map(PointRule::getPoints).orElse(action.defaultPoints());
    }

    private boolean awardOnce(Long userId, PointAction action, int amount, String description, String stream,
                              String sourceType, String sourceId) {
        gamification.ensureRow(userId);
        String dedupeKey = action.name() + ":" + sourceId;
        if (ledger.insertOnce(userId, amount, action.name(), truncate(description, 255), stream, sourceType, sourceId, dedupeKey) == 0) {
            return false;
        }
        gamification.addPoints(userId, amount);
        if (action != PointAction.BADGE_UNLOCKED
                && gamification.findById(userId).map(UserGamification::getTotalPoints).orElse(0) >= 500) {
            unlockBadge(userId, "XP_EXPLORER");
        }
        return true;
    }

    private Optional<Badge> unlockBadge(Long userId, String badgeId) {
        if (userBadges.unlockOnce(userId, badgeId) == 0) {
            return Optional.empty();
        }
        Badge badge = badges.findById(badgeId).orElseThrow();
        if (badge.getPointsReward() > 0) {
            awardOnce(userId, PointAction.BADGE_UNLOCKED, badge.getPointsReward(), "Unlocked badge: " + badge.getTitle(), null,
                    "BADGE", badgeId);
        }
        return Optional.of(badge);
    }

    // ---- Admin --------------------------------------------------------------------------------------------------------

    /** A manual correction by an admin, always with a reason, always audited. Positive or negative, within a sane range. */
    @Transactional
    public void adjustPointsManual(Long userId, int amount, String reason, String adminEmail) {
        String cleanReason = reason == null ? "" : reason.strip();
        if (cleanReason.length() < MIN_REASON || cleanReason.length() > MAX_REASON) {
            throw new InvalidGamificationRequestException("Give a reason of " + MIN_REASON + " to " + MAX_REASON + " characters.");
        }
        if (amount == 0 || Math.abs(amount) > MAX_ADJUSTMENT) {
            throw new InvalidGamificationRequestException("The adjustment must be between 1 and " + MAX_ADJUSTMENT + " XP, up or down.");
        }
        User learner = userId == null ? null : users.findById(userId).orElse(null);
        if (learner == null) {
            throw new InvalidGamificationRequestException("That learner doesn't exist.");
        }
        if (learner.isAdmin()) {
            throw new InvalidGamificationRequestException("Admins don't earn XP.");
        }

        gamification.ensureRow(userId);
        gamification.addPoints(userId, amount);
        ledger.save(new PointTransaction(userId, amount, PointAction.MANUAL_ADJUSTMENT.name(),
                "Admin XP adjustment: " + (amount > 0 ? "+" : "") + amount + " XP. Reason: " + cleanReason, null));
        audit.log(adminEmail, "MANUAL_XP_ADJUSTMENT", null,
                "Adjusted " + amount + " XP for " + learner.getEmail() + ". Reason: " + cleanReason);
    }

    @Transactional(readOnly = true)
    public List<PointRule> pointRules() {
        return rules.findAll();
    }

    @Transactional
    public PointRule updatePointRule(String actionType, int points, String adminEmail) {
        PointAction action = PointAction.find(actionType).filter(PointAction::isPriceable)
                .orElseThrow(() -> new InvalidGamificationRequestException("That isn't a rule you can change."));
        if (points < 0 || points > MAX_RULE_POINTS) {
            throw new InvalidGamificationRequestException("Points must be between 0 and " + MAX_RULE_POINTS + ".");
        }
        PointRule rule = rules.findByActionType(action.name())
                .orElseGet(() -> new PointRule(action.name(), action.name(), action.name(), action.defaultPoints(), null));
        int before = rule.getPoints();
        rule.setPoints(points);
        PointRule saved = rules.save(rule);
        audit.log(adminEmail, "UPDATE_POINT_RULE", null, "Changed XP rule " + action.name() + " from " + before + " to " + points);
        return saved;
    }

    // ---- Helpers ------------------------------------------------------------------------------------------------------

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
