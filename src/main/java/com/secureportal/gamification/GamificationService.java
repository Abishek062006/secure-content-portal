package com.secureportal.gamification;

import com.secureportal.audit.AuditService;
import com.secureportal.user.Role;
import com.secureportal.user.User;
import com.secureportal.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class GamificationService {

    private final UserGamificationRepository gamificationRepository;
    private final BadgeRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final PointTransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final PointRuleRepository pointRuleRepository;
    private final AuditService auditService;
    private final com.secureportal.course.CourseRepository courseRepository;

    public GamificationService(UserGamificationRepository gamificationRepository,
                               BadgeRepository badgeRepository,
                               UserBadgeRepository userBadgeRepository,
                               PointTransactionRepository transactionRepository,
                               UserRepository userRepository,
                               PointRuleRepository pointRuleRepository,
                               AuditService auditService,
                               com.secureportal.course.CourseRepository courseRepository) {
        this.gamificationRepository = gamificationRepository;
        this.badgeRepository = badgeRepository;
        this.userBadgeRepository = userBadgeRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.pointRuleRepository = pointRuleRepository;
        this.auditService = auditService;
        this.courseRepository = courseRepository;
    }

    @Transactional
    public UserGamification getOrCreateGamification(Long userId) {
        return gamificationRepository.findById(userId).orElseGet(() -> {
            UserGamification ug = new UserGamification(userId);
            return gamificationRepository.save(ug);
        });
    }

    public record CheckInResult(int pointsEarned, int newStreak, boolean claimedToday, List<Badge> unlockedBadges) {}

    public int getRulePoints(String actionType, int defaultPoints) {
        return pointRuleRepository.findByActionType(actionType)
                .map(PointRule::getPoints)
                .orElse(defaultPoints);
    }

    @Transactional
    public CheckInResult checkIn(Long userId) {
        UserGamification ug = getOrCreateGamification(userId);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        if (Objects.equals(ug.getLastCheckinDate(), today)) {
            return new CheckInResult(0, ug.getCurrentStreak(), true, List.of());
        }

        int newStreak = 1;
        if (ug.getLastCheckinDate() != null) {
            long daysBetween = ChronoUnit.DAYS.between(ug.getLastCheckinDate(), today);
            if (daysBetween == 1) {
                newStreak = ug.getCurrentStreak() + 1;
            }
        }

        ug.setCurrentStreak(newStreak);
        ug.setLastCheckinDate(today);
        if (newStreak > ug.getMaxStreak()) {
            ug.setMaxStreak(newStreak);
        }

        int basePoints = getRulePoints("DAILY_CHECKIN", 5);
        int bonusPoints = 0;
        if (newStreak % 7 == 0) {
            bonusPoints = 25; // 7-day milestone
        } else if (newStreak % 3 == 0) {
            bonusPoints = 10; // 3-day bonus
        }
        int totalEarned = basePoints + bonusPoints;

        ug.addPoints(totalEarned);
        gamificationRepository.save(ug);

        transactionRepository.save(new PointTransaction(
                userId, totalEarned, "DAILY_CHECKIN",
                "Daily Check-in (" + newStreak + " day streak)", null,
                "DAILY_CHECKIN", today.toString()
        ));

        List<Badge> newlyUnlocked = new ArrayList<>();
        if (newStreak >= 3) {
            unlockBadgeInternal(userId, "STREAK_MASTER").ifPresent(newlyUnlocked::add);
        }
        if (newStreak >= 7) {
            unlockBadgeInternal(userId, "STREAK_7").ifPresent(newlyUnlocked::add);
        }
        if (newStreak >= 30) {
            unlockBadgeInternal(userId, "STREAK_30").ifPresent(newlyUnlocked::add);
        }

        return new CheckInResult(totalEarned, newStreak, false, newlyUnlocked);
    }

    @Transactional
    public List<Badge> recordLessonCompletion(Long userId, String stream) {
        return recordLessonCompletion(userId, null, stream);
    }

    @Transactional
    public List<Badge> recordLessonCompletion(Long userId, String lessonId, String stream) {
        if (lessonId != null && transactionRepository.existsByUserIdAndActionTypeAndSourceId(userId, "LESSON_COMPLETED", lessonId)) {
            return List.of(); // Prevent duplicate XP for completing the same lesson
        }

        int points = getRulePoints("LESSON_COMPLETED", 10);
        awardPointsWithSource(userId, points, "LESSON_COMPLETED", "Completed a video lesson", stream, "LESSON", lessonId);

        List<Badge> unlocked = new ArrayList<>();
        unlockBadgeInternal(userId, "FAST_LEARNER").ifPresent(unlocked::add);
        return unlocked;
    }

    @Transactional
    public List<Badge> recordQuizAttempt(Long userId, int score, boolean passed, String stream) {
        return recordQuizAttempt(userId, null, score, passed, stream);
    }

    @Transactional
    public List<Badge> recordQuizAttempt(Long userId, String quizId, int score, boolean passed, String stream) {
        List<Badge> unlocked = new ArrayList<>();
        if (passed) {
            if (quizId == null || !transactionRepository.existsByUserIdAndActionTypeAndSourceId(userId, "QUIZ_PASSED", quizId)) {
                int points = getRulePoints("QUIZ_PASSED", 20);
                awardPointsWithSource(userId, points, "QUIZ_PASSED", "Passed quiz with " + score + "% score", stream, "QUIZ", quizId);
            }

            long passedCountOverall = transactionRepository.countByUserIdAndActionType(userId, "QUIZ_PASSED");
            if (passedCountOverall >= 5) {
                unlockBadgeInternal(userId, "QUIZ_MASTER").ifPresent(unlocked::add);
            }
        }

        if (score == 100) {
            unlockBadgeInternal(userId, "QUIZ_ACE").ifPresent(unlocked::add);
        }

        Instant last24h = Instant.now().minus(24, ChronoUnit.HOURS);
        long passedCount = transactionRepository.countByUserIdAndActionTypeAndCreatedAtAfter(userId, "QUIZ_PASSED", last24h);
        if (passedCount >= 3) {
            unlockBadgeInternal(userId, "SPEED_DEMON").ifPresent(unlocked::add);
        }

        return unlocked;
    }

    @Transactional
    public List<Badge> recordCourseCompletion(Long userId, String stream) {
        return recordCourseCompletion(userId, null, stream);
    }

    @Transactional
    public List<Badge> recordCourseCompletion(Long userId, String courseId, String stream) {
        if (courseId != null && transactionRepository.existsByUserIdAndActionTypeAndSourceId(userId, "COURSE_COMPLETED", courseId)) {
            return List.of(); // Prevent duplicate XP for completing the same course
        }

        int points = getRulePoints("COURSE_COMPLETED", 100);
        awardPointsWithSource(userId, points, "COURSE_COMPLETED", "Completed full course", stream, "COURSE", courseId);

        List<Badge> unlocked = new ArrayList<>();
        unlockBadgeInternal(userId, "COURSE_GRADUATE").ifPresent(unlocked::add);
        unlockBadgeInternal(userId, "COURSE_STARTER").ifPresent(unlocked::add);

        if (stream != null) {
            long streamCoursesCompleted = transactionRepository.countByUserIdAndActionTypeAndStream(userId, "COURSE_COMPLETED", stream);
            if (streamCoursesCompleted >= 2) {
                unlockBadgeInternal(userId, "STREAM_SPECIALIST").ifPresent(unlocked::add);
            }

            String cleanStream = stream.toLowerCase();
            if (cleanStream.contains("web") || cleanStream.contains("engineering") || cleanStream.contains("code")) {
                unlockBadgeInternal(userId, "WEB_DEV_PIONEER").ifPresent(unlocked::add);
            } else if (cleanStream.contains("ai") || cleanStream.contains("data") || cleanStream.contains("ml")) {
                unlockBadgeInternal(userId, "AI_EXPLORER").ifPresent(unlocked::add);
            }
        }
        return unlocked;
    }

    @Transactional
    public void awardPoints(Long userId, int amount, String actionType, String description, String stream) {
        awardPointsWithSource(userId, amount, actionType, description, stream, null, null);
    }

    @Transactional
    public void awardPointsWithSource(Long userId, int amount, String actionType, String description, String stream, String sourceType, String sourceId) {
        UserGamification ug = getOrCreateGamification(userId);
        ug.addPoints(amount);
        gamificationRepository.save(ug);

        transactionRepository.save(new PointTransaction(userId, amount, actionType, description, stream, sourceType, sourceId));

        if (ug.getTotalPoints() >= 500) {
            unlockBadgeInternal(userId, "XP_EXPLORER");
        }
    }

    @Transactional
    public void adjustPointsManual(Long userId, int amount, String reason, String adminEmail) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Reason is required for manual XP adjustment");
        }
        if (amount == 0) {
            throw new IllegalArgumentException("Adjustment amount cannot be zero");
        }

        UserGamification ug = getOrCreateGamification(userId);
        ug.addPoints(amount);
        gamificationRepository.save(ug);

        String description = "Admin XP Adjustment: " + (amount > 0 ? "+" : "") + amount + " XP. Reason: " + reason;
        transactionRepository.save(new PointTransaction(userId, amount, "MANUAL_ADJUSTMENT", description, null, "ADMIN", adminEmail));

        auditService.log(adminEmail, "MANUAL_XP_ADJUSTMENT", null, "Adjusted " + amount + " XP for user ID " + userId + ". Reason: " + reason);
    }

    @Transactional
    public PointRule updatePointRule(String actionType, int newPoints, String adminEmail) {
        if (newPoints < 0) {
            throw new IllegalArgumentException("Points value cannot be negative");
        }
        PointRule rule = pointRuleRepository.findByActionType(actionType)
                .orElseGet(() -> new PointRule(actionType, actionType, actionType, newPoints, "Custom rule"));

        int oldPoints = rule.getPoints();
        rule.setPoints(newPoints);
        PointRule saved = pointRuleRepository.save(rule);

        auditService.log(adminEmail, "UPDATE_POINT_RULE", null, "Updated XP rule [" + actionType + "] from " + oldPoints + " to " + newPoints + " XP");
        return saved;
    }

    public List<PointRule> getAllPointRules() {
        return pointRuleRepository.findAll();
    }

    public List<PointTransaction> getPointHistory(Long userId, String actionType, String stream) {
        return transactionRepository.filterTransactions(userId, actionType, stream);
    }

    private Optional<Badge> unlockBadgeInternal(Long userId, String badgeId) {
        if (userBadgeRepository.existsByUserIdAndBadgeId(userId, badgeId)) {
            return Optional.empty();
        }

        Optional<Badge> badgeOpt = badgeRepository.findById(badgeId);
        if (badgeOpt.isEmpty()) {
            return Optional.empty();
        }

        Badge badge = badgeOpt.get();
        userBadgeRepository.save(new UserBadge(userId, badgeId));

        if (badge.getPointsReward() > 0) {
            UserGamification ug = getOrCreateGamification(userId);
            ug.addPoints(badge.getPointsReward());
            gamificationRepository.save(ug);

            transactionRepository.save(new PointTransaction(
                    userId, badge.getPointsReward(), "BADGE_UNLOCKED",
                    "Unlocked badge: " + badge.getTitle(), null
            ));
        }

        return Optional.of(badge);
    }

    public record LeaderboardEntry(
            int rank,
            Long userId,
            String displayName,
            String pictureUrl,
            int points,
            int streak,
            long badgeCount,
            boolean isCurrentUser
    ) {}

    public record LeaderboardResponse(
            String timeframe,
            String stream,
            List<LeaderboardEntry> podium,
            List<LeaderboardEntry> rankings,
            LeaderboardEntry currentUserRank,
            long totalParticipants
    ) {}

    public LeaderboardResponse getLeaderboard(String timeframe, String streamFilter, Long currentUserId) {
        String normalizedTimeframe = timeframe == null ? "all_time" : timeframe.toLowerCase();
        String normalizedStream = (streamFilter == null || streamFilter.equalsIgnoreCase("all")) ? null : streamFilter;

        List<LeaderboardEntry> allEntries = new ArrayList<>();

        if ("all_time".equals(normalizedTimeframe) && normalizedStream == null) {
            // Fast path for all time global
            List<UserGamification> list = gamificationRepository.findAllByOrderByTotalPointsDesc();
            Map<Long, User> userMap = getUserMap(list.stream().map(UserGamification::getUserId).toList());
            Map<Long, Long> badgeCountMap = getBadgeCountMap();

            int rank = 1;
            for (UserGamification ug : list) {
                User u = userMap.get(ug.getUserId());
                if (u == null || u.getRole() == Role.ADMIN) continue;

                allEntries.add(new LeaderboardEntry(
                        rank++,
                        ug.getUserId(),
                        u.getDisplayName(),
                        u.getPictureUrl(),
                        ug.getTotalPoints(),
                        ug.getCurrentStreak(),
                        badgeCountMap.getOrDefault(ug.getUserId(), 0L),
                        Objects.equals(ug.getUserId(), currentUserId)
                ));
            }
        } else {
            // Aggregate from point transactions
            Instant startDate = getStartDateForTimeframe(normalizedTimeframe);
            List<Object[]> rawList = startDate == null
                    ? transactionRepository.findLeaderboardAllTimeAndStream(normalizedStream)
                    : transactionRepository.findLeaderboardByDateRangeAndStream(startDate, normalizedStream);

            List<Long> userIds = rawList.stream().map(r -> (Long) r[0]).toList();
            Map<Long, User> userMap = getUserMap(userIds);
            Map<Long, UserGamification> gamificationMap = getGamificationMap(userIds);
            Map<Long, Long> badgeCountMap = getBadgeCountMap();

            int rank = 1;
            for (Object[] row : rawList) {
                Long uId = (Long) row[0];
                Number totalPts = (Number) row[1];
                User u = userMap.get(uId);
                if (u == null || u.getRole() == Role.ADMIN) continue;

                UserGamification ug = gamificationMap.get(uId);
                int streak = ug != null ? ug.getCurrentStreak() : 0;

                allEntries.add(new LeaderboardEntry(
                        rank++,
                        uId,
                        u.getDisplayName(),
                        u.getPictureUrl(),
                        totalPts.intValue(),
                        streak,
                        badgeCountMap.getOrDefault(uId, 0L),
                        Objects.equals(uId, currentUserId)
                ));
            }
        }

        LeaderboardEntry currentUserRank = allEntries.stream()
                .filter(e -> Objects.equals(e.userId(), currentUserId))
                .findFirst()
                .orElseGet(() -> {
                    if (currentUserId == null) return null;
                    User u = userRepository.findById(currentUserId).orElse(null);
                    if (u == null || u.getRole() == Role.ADMIN) return null;
                    UserGamification ug = getOrCreateGamification(currentUserId);
                    long bCount = userBadgeRepository.countByUserId(currentUserId);
                    long rank = gamificationRepository.findRankByPoints(ug.getTotalPoints());
                    return new LeaderboardEntry(
                            (int) rank,
                            currentUserId,
                            u.getDisplayName(),
                            u.getPictureUrl(),
                            ug.getTotalPoints(),
                            ug.getCurrentStreak(),
                            bCount,
                            true
                    );
                });

        List<LeaderboardEntry> podium = allEntries.stream().limit(3).toList();
        List<LeaderboardEntry> rankings = allEntries.stream().skip(3).limit(50).toList();

        return new LeaderboardResponse(
                normalizedTimeframe,
                streamFilter == null ? "all" : streamFilter,
                podium,
                rankings,
                currentUserRank,
                allEntries.size()
        );
    }

    public record UserBadgeDto(
            String id,
            String title,
            String description,
            String category,
            String icon,
            int pointsReward,
            String rarity,
            boolean unlocked,
            Instant unlockedAt
    ) {}

    public List<UserBadgeDto> getBadgesForUser(Long userId) {
        List<Badge> allBadges = badgeRepository.findAll();
        List<UserBadge> userBadges = userBadgeRepository.findByUserId(userId);
        Map<String, Instant> unlockedMap = new HashMap<>();
        for (UserBadge ub : userBadges) {
            unlockedMap.put(ub.getBadgeId(), ub.getUnlockedAt());
        }

        return allBadges.stream().map(b -> new UserBadgeDto(
                b.getId(),
                b.getTitle(),
                b.getDescription(),
                b.getCategory(),
                b.getIcon(),
                b.getPointsReward(),
                b.getRarity(),
                unlockedMap.containsKey(b.getId()),
                unlockedMap.get(b.getId())
        )).toList();
    }

    private Instant getStartDateForTimeframe(String timeframe) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return switch (timeframe) {
            case "daily", "today" -> today.atStartOfDay(ZoneOffset.UTC).toInstant();
            case "weekly", "this_week" -> today.with(java.time.DayOfWeek.MONDAY).atStartOfDay(ZoneOffset.UTC).toInstant();
            case "monthly", "this_month" -> today.withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            default -> null; // all_time
        };
    }

    private Map<Long, User> getUserMap(List<Long> userIds) {
        Map<Long, User> map = new HashMap<>();
        if (!userIds.isEmpty()) {
            userRepository.findAllById(userIds).forEach(u -> map.put(u.getId(), u));
        }
        return map;
    }

    private Map<Long, UserGamification> getGamificationMap(List<Long> userIds) {
        Map<Long, UserGamification> map = new HashMap<>();
        if (!userIds.isEmpty()) {
            gamificationRepository.findAllById(userIds).forEach(g -> map.put(g.getUserId(), g));
        }
        return map;
    }

    private Map<Long, Long> getBadgeCountMap() {
        Map<Long, Long> map = new HashMap<>();
        for (UserBadge ub : userBadgeRepository.findAll()) {
            map.put(ub.getUserId(), map.getOrDefault(ub.getUserId(), 0L) + 1);
        }
        return map;
    }

    public List<String> getActiveStreams() {
        List<String> categories = courseRepository.findDistinctCategories();
        if (categories == null || categories.isEmpty()) {
            return List.of(
                "Engineering & Web Dev",
                "AI & Data Science",
                "UI/UX & Design",
                "Cloud & Infrastructure"
            );
        }
        return categories;
    }

    public record AdminLeaderboardDetail(
            int rank,
            Long userId,
            String displayName,
            String email,
            String pictureUrl,
            String stream,
            int points,
            int streak,
            long badgeCount,
            long coursesCompleted,
            long quizzesPassed
    ) {}

    public record AdminLeaderboardResponse(
            long totalParticipants,
            long activeLearners,
            long totalXpAwarded,
            long totalBadgesEarned,
            double avgXpPerLearner,
            List<AdminLeaderboardDetail> rankings
    ) {}

    public AdminLeaderboardResponse getAdminLeaderboard(String timeframe, String streamFilter) {
        LeaderboardResponse res = getLeaderboard(timeframe, streamFilter, null);
        List<LeaderboardEntry> all = new ArrayList<>();
        all.addAll(res.podium());
        all.addAll(res.rankings());

        List<Long> userIds = all.stream().map(LeaderboardEntry::userId).toList();
        Map<Long, User> userMap = getUserMap(userIds);
        Map<Long, Long> coursesCompletedMap = new HashMap<>();
        Map<Long, Long> quizzesPassedMap = new HashMap<>();

        for (Long uId : userIds) {
            coursesCompletedMap.put(uId, transactionRepository.countByUserIdAndActionType(uId, "COURSE_COMPLETED"));
            quizzesPassedMap.put(uId, transactionRepository.countByUserIdAndActionType(uId, "QUIZ_PASSED"));
        }

        List<AdminLeaderboardDetail> adminRankings = new ArrayList<>();
        long totalXp = 0;
        long totalBadges = 0;
        long activeLearners = 0;

        for (LeaderboardEntry e : all) {
            User u = userMap.get(e.userId());
            String email = u != null ? u.getEmail() : "N/A";
            totalXp += e.points();
            totalBadges += e.badgeCount();
            if (e.streak() > 0) activeLearners++;

            adminRankings.add(new AdminLeaderboardDetail(
                    e.rank(),
                    e.userId(),
                    e.displayName(),
                    email,
                    e.pictureUrl(),
                    streamFilter == null ? "All Streams" : streamFilter,
                    e.points(),
                    e.streak(),
                    e.badgeCount(),
                    coursesCompletedMap.getOrDefault(e.userId(), 0L),
                    quizzesPassedMap.getOrDefault(e.userId(), 0L)
            ));
        }

        long totalParticipants = adminRankings.size();
        double avgXp = totalParticipants > 0 ? (double) totalXp / totalParticipants : 0.0;

        return new AdminLeaderboardResponse(
                totalParticipants,
                activeLearners,
                totalXp,
                totalBadges,
                Math.round(avgXp * 10.0) / 10.0,
                adminRankings
        );
    }
}
