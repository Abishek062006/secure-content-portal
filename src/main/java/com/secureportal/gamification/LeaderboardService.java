package com.secureportal.gamification;

import com.secureportal.course.CourseRepository;
import com.secureportal.infra.TtlCache;
import com.secureportal.user.Role;
import com.secureportal.user.User;
import com.secureportal.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Rankings of learners (admins never take part). Only the top of the board is ever read, in one bounded query, and that top
 * is cached briefly and shared through Redis when it is on, so the leaderboard costs the same with ten learners or a hundred
 * thousand. A learner's own row is looked up separately.
 */
@Service
public class LeaderboardService {

    static final int PODIUM = 3;
    static final int LISTED = 50;
    private static final int FETCH = PODIUM + LISTED;
    private static final List<String> DEFAULT_STREAMS =
            List.of("Engineering & Web Dev", "AI & Data Science", "UI/UX & Design", "Cloud & Infrastructure");

    private final UserGamificationRepository gamification;
    private final PointTransactionRepository ledger;
    private final UserBadgeRepository userBadges;
    private final UserRepository users;
    private final CourseRepository courses;
    private final TtlCache cache;
    private final Duration cacheTtl;

    public LeaderboardService(UserGamificationRepository gamification, PointTransactionRepository ledger, UserBadgeRepository userBadges,
                              UserRepository users, CourseRepository courses, TtlCache cache,
                              @Value("${app.cache.leaderboard-seconds:30}") long cacheSeconds) {
        this.gamification = gamification;
        this.ledger = ledger;
        this.userBadges = userBadges;
        this.users = users;
        this.courses = courses;
        this.cache = cache;
        this.cacheTtl = Duration.ofSeconds(cacheSeconds);
    }

    public record LeaderboardEntry(int rank, Long userId, String displayName, String pictureUrl, int points, int streak,
                                   long badgeCount, boolean isCurrentUser) {
    }

    public record LeaderboardResponse(String timeframe, String stream, List<LeaderboardEntry> podium,
                                      List<LeaderboardEntry> rankings, LeaderboardEntry currentUserRank, long totalParticipants) {
    }

    /** One ranked learner, without the per-viewer "is this me" flag, so it can be cached and shared. */
    record Standing(int rank, Long userId, String displayName, String pictureUrl, int points, int streak, long badgeCount)
            implements Serializable {
        LeaderboardEntry entry(Long viewerId) {
            return new LeaderboardEntry(rank, userId, displayName, pictureUrl, points, streak, badgeCount, Objects.equals(userId, viewerId));
        }
    }

    record TopPage(List<Standing> top, long participants) implements Serializable {
    }

    @Transactional(readOnly = true)
    public LeaderboardResponse leaderboard(String timeframe, String streamFilter, Long viewerId) {
        String period = normalizePeriod(timeframe);
        String stream = normalizeStream(streamFilter);
        TopPage page = cache.get("leaderboard:" + period + ":" + (stream == null ? "*" : stream), cacheTtl, () -> loadTop(period, stream));

        List<LeaderboardEntry> entries = page.top().stream().map(s -> s.entry(viewerId)).toList();
        LeaderboardEntry mine = entries.stream().filter(LeaderboardEntry::isCurrentUser).findFirst()
                .orElseGet(() -> viewerId == null ? null : standingOutsideTop(viewerId, period, stream));

        return new LeaderboardResponse(period, stream == null ? "all" : stream,
                entries.stream().limit(PODIUM).toList(),
                entries.stream().skip(PODIUM).limit(LISTED).toList(),
                mine, page.participants());
    }

    public record AdminLeaderboardDetail(int rank, Long userId, String displayName, String email, String pictureUrl, String stream,
                                         int points, int streak, long badgeCount, long coursesCompleted, long quizzesPassed) {
    }

    public record AdminLeaderboardResponse(long totalParticipants, long activeLearners, long totalXpAwarded, long totalBadgesEarned,
                                           double avgXpPerLearner, List<AdminLeaderboardDetail> rankings) {
    }

    /** The admin's view: the same top of the board with contact details and course/quiz counts, plus platform-wide totals. */
    @Transactional(readOnly = true)
    public AdminLeaderboardResponse adminLeaderboard(String timeframe, String streamFilter) {
        String period = normalizePeriod(timeframe);
        String stream = normalizeStream(streamFilter);
        TopPage page = loadTop(period, stream);

        List<Long> ids = page.top().stream().map(Standing::userId).toList();
        Map<Long, String> emails = new HashMap<>();
        if (!ids.isEmpty()) {
            users.findAllById(ids).forEach(u -> emails.put(u.getId(), u.getEmail()));
        }
        Map<Long, Long> courseCounts = countsByUser(ids, PointAction.COURSE_COMPLETED);
        Map<Long, Long> quizCounts = countsByUser(ids, PointAction.QUIZ_PASSED);

        List<AdminLeaderboardDetail> rankings = page.top().stream()
                .map(s -> new AdminLeaderboardDetail(s.rank(), s.userId(), s.displayName(), emails.getOrDefault(s.userId(), ""),
                        s.pictureUrl(), stream == null ? "All Streams" : stream, s.points(), s.streak(), s.badgeCount(),
                        courseCounts.getOrDefault(s.userId(), 0L), quizCounts.getOrDefault(s.userId(), 0L)))
                .toList();

        long learners = gamification.countLearners(Role.ADMIN);
        long totalXp = gamification.sumPoints(Role.ADMIN);
        double average = learners == 0 ? 0.0 : Math.round(totalXp * 10.0 / learners) / 10.0;
        return new AdminLeaderboardResponse(learners, gamification.countWithStreak(Role.ADMIN), totalXp, userBadges.countAll(),
                average, rankings);
    }

    /** The streams learners can filter by: the categories courses actually use. */
    @Transactional(readOnly = true)
    public List<String> streams() {
        List<String> categories = courses.findDistinctCategories();
        return categories == null || categories.isEmpty() ? DEFAULT_STREAMS : categories;
    }

    // ---- Building the board -------------------------------------------------------------------------------------------

    private TopPage loadTop(String period, String stream) {
        Instant since = startOf(period);
        List<Long> ids = new ArrayList<>();
        List<Integer> points = new ArrayList<>();
        Map<Long, Integer> streaks = new HashMap<>();
        long participants;

        if (since == null && stream == null) {
            List<UserGamification> top = gamification.topLearners(Role.ADMIN, PageRequest.of(0, FETCH));
            top.forEach(g -> {
                ids.add(g.getUserId());
                points.add(g.getTotalPoints());
                streaks.put(g.getUserId(), g.getCurrentStreak());
            });
            participants = gamification.countLearners(Role.ADMIN);
        } else {
            Instant from = since == null ? Instant.EPOCH : since;
            for (Object[] row : ledger.topSince(from, stream, FETCH)) {
                ids.add(((Number) row[0]).longValue());
                points.add(((Number) row[1]).intValue());
            }
            if (!ids.isEmpty()) {
                gamification.findAllById(ids).forEach(g -> streaks.put(g.getUserId(), g.getCurrentStreak()));
            }
            participants = ledger.countParticipantsSince(from, stream);
        }

        Map<Long, User> people = new HashMap<>();
        Map<Long, Long> badgeCounts = new HashMap<>();
        if (!ids.isEmpty()) {
            users.findAllById(ids).forEach(u -> people.put(u.getId(), u));
            userBadges.countByUsers(ids).forEach(r -> badgeCounts.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue()));
        }

        List<Standing> top = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            User person = people.get(ids.get(i));
            if (person == null) continue;
            top.add(new Standing(top.size() + 1, person.getId(), person.getDisplayName(), person.getPictureUrl(), points.get(i),
                    streaks.getOrDefault(person.getId(), 0), badgeCounts.getOrDefault(person.getId(), 0L)));
        }
        return new TopPage(top, participants);
    }

    /** A viewer who isn't in the top page still sees their own rank. Admins have none. */
    private LeaderboardEntry standingOutsideTop(Long viewerId, String period, String stream) {
        User viewer = users.findById(viewerId).orElse(null);
        if (viewer == null || viewer.isAdmin()) {
            return null;
        }
        Instant since = startOf(period);
        UserGamification mine = gamification.findById(viewerId).orElse(null);
        int points;
        long rank;
        if (since == null && stream == null) {
            points = mine == null ? 0 : mine.getTotalPoints();
            rank = gamification.rankOf(points, Role.ADMIN);
        } else {
            Instant from = since == null ? Instant.EPOCH : since;
            points = (int) ledger.pointsSince(viewerId, from, stream);
            rank = ledger.rankSince(from, stream, points);
        }
        return new LeaderboardEntry((int) rank, viewerId, viewer.getDisplayName(), viewer.getPictureUrl(), points,
                mine == null ? 0 : mine.getCurrentStreak(), userBadges.countByUserId(viewerId), true);
    }

    private Map<Long, Long> countsByUser(List<Long> ids, PointAction action) {
        Map<Long, Long> counts = new HashMap<>();
        if (!ids.isEmpty()) {
            ledger.countByUsersAndAction(ids, action.name()).forEach(r -> counts.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue()));
        }
        return counts;
    }

    // ---- Inputs -------------------------------------------------------------------------------------------------------

    private static String normalizePeriod(String timeframe) {
        String value = timeframe == null ? "all_time" : timeframe.toLowerCase();
        return switch (value) {
            case "daily", "today" -> "daily";
            case "weekly", "this_week" -> "weekly";
            case "monthly", "this_month" -> "monthly";
            default -> "all_time";
        };
    }

    private static String normalizeStream(String stream) {
        if (stream == null || stream.isBlank() || stream.equalsIgnoreCase("all")) {
            return null;
        }
        String clean = stream.strip();
        return clean.length() > 100 ? clean.substring(0, 100) : clean;
    }

    private static Instant startOf(String period) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return switch (period) {
            case "daily" -> today.atStartOfDay(ZoneOffset.UTC).toInstant();
            case "weekly" -> today.with(java.time.DayOfWeek.MONDAY).atStartOfDay(ZoneOffset.UTC).toInstant();
            case "monthly" -> today.withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            default -> null;
        };
    }
}
