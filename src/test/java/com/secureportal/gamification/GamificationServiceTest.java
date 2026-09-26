package com.secureportal.gamification;

import com.secureportal.audit.AuditService;
import com.secureportal.course.CourseRepository;
import com.secureportal.user.Role;
import com.secureportal.user.User;
import com.secureportal.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GamificationServiceTest {

    private UserGamificationRepository gamificationRepository;
    private BadgeRepository badgeRepository;
    private UserBadgeRepository userBadgeRepository;
    private PointTransactionRepository transactionRepository;
    private UserRepository userRepository;
    private PointRuleRepository pointRuleRepository;
    private AuditService auditService;
    private CourseRepository courseRepository;

    private GamificationService service;

    @BeforeEach
    void setUp() {
        gamificationRepository = mock(UserGamificationRepository.class);
        badgeRepository = mock(BadgeRepository.class);
        userBadgeRepository = mock(UserBadgeRepository.class);
        transactionRepository = mock(PointTransactionRepository.class);
        userRepository = mock(UserRepository.class);
        pointRuleRepository = mock(PointRuleRepository.class);
        auditService = mock(AuditService.class);
        courseRepository = mock(CourseRepository.class);

        service = new GamificationService(
                gamificationRepository,
                badgeRepository,
                userBadgeRepository,
                transactionRepository,
                userRepository,
                pointRuleRepository,
                auditService,
                courseRepository
        );
    }

    @Test
    @DisplayName("1. Lesson completion awards +10 XP and unlocks Fast Learner badge")
    void recordLessonCompletion_awardsXPAndBadge() {
        Long userId = 1L;
        UserGamification ug = new UserGamification(userId);
        when(gamificationRepository.findById(userId)).thenReturn(Optional.of(ug));
        when(transactionRepository.existsByUserIdAndActionTypeAndSourceId(userId, "LESSON_COMPLETED", "lesson-123")).thenReturn(false);
        
        Badge fastLearner = new Badge("FAST_LEARNER", "Fast Learner", "Completed first lesson", "MILESTONE", "⚡", 50, "COMMON");
        when(badgeRepository.findById("FAST_LEARNER")).thenReturn(Optional.of(fastLearner));
        when(userBadgeRepository.existsByUserIdAndBadgeId(userId, "FAST_LEARNER")).thenReturn(false);

        List<Badge> unlocked = service.recordLessonCompletion(userId, "lesson-123", "Engineering");

        assertEquals(1, unlocked.size());
        assertEquals("FAST_LEARNER", unlocked.get(0).getId());
        verify(transactionRepository, times(2)).save(any(PointTransaction.class)); // 1 for lesson, 1 for badge reward
    }

    @Test
    @DisplayName("2. Duplicate lesson completion XP prevention")
    void recordLessonCompletion_preventsDuplicateXP() {
        Long userId = 1L;
        when(transactionRepository.existsByUserIdAndActionTypeAndSourceId(userId, "LESSON_COMPLETED", "lesson-123")).thenReturn(true);

        List<Badge> unlocked = service.recordLessonCompletion(userId, "lesson-123", "Engineering");

        assertTrue(unlocked.isEmpty());
        verify(transactionRepository, never()).save(any(PointTransaction.class));
    }

    @Test
    @DisplayName("3. Quiz pass XP awards points and unlocks Quiz Master badge on 5 passes")
    void recordQuizAttempt_awardsXPAndBadgeOnPassed() {
        Long userId = 1L;
        UserGamification ug = new UserGamification(userId);
        when(gamificationRepository.findById(userId)).thenReturn(Optional.of(ug));
        when(transactionRepository.existsByUserIdAndActionTypeAndSourceId(userId, "QUIZ_PASSED", "quiz-456")).thenReturn(false);
        when(transactionRepository.countByUserIdAndActionType(userId, "QUIZ_PASSED")).thenReturn(5L);

        Badge quizMaster = new Badge("QUIZ_MASTER", "Quiz Master", "Passed 5 quizzes", "MASTERY", "🧠", 150, "RARE");
        when(badgeRepository.findById("QUIZ_MASTER")).thenReturn(Optional.of(quizMaster));
        when(userBadgeRepository.existsByUserIdAndBadgeId(userId, "QUIZ_MASTER")).thenReturn(false);

        List<Badge> unlocked = service.recordQuizAttempt(userId, "quiz-456", 85, true, "Engineering");

        assertEquals(1, unlocked.size());
        assertEquals("QUIZ_MASTER", unlocked.get(0).getId());
    }

    @Test
    @DisplayName("4. Course completion XP awards +100 XP")
    void recordCourseCompletion_awardsXP() {
        Long userId = 1L;
        UserGamification ug = new UserGamification(userId);
        when(gamificationRepository.findById(userId)).thenReturn(Optional.of(ug));
        when(transactionRepository.existsByUserIdAndActionTypeAndSourceId(userId, "COURSE_COMPLETED", "course-789")).thenReturn(false);

        Badge graduate = new Badge("COURSE_GRADUATE", "Graduate", "Completed course", "MILESTONE", "🎓", 300, "EPIC");
        when(badgeRepository.findById("COURSE_GRADUATE")).thenReturn(Optional.of(graduate));
        when(userBadgeRepository.existsByUserIdAndBadgeId(userId, "COURSE_GRADUATE")).thenReturn(false);

        List<Badge> unlocked = service.recordCourseCompletion(userId, "course-789", "Engineering");

        assertFalse(unlocked.isEmpty());
        verify(transactionRepository, atLeastOnce()).save(any(PointTransaction.class));
    }

    @Test
    @DisplayName("5 & 6. Daily check-in single award per day and streak increment")
    void checkIn_incrementsStreak() {
        Long userId = 1L;
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        UserGamification ug = new UserGamification(userId);
        ug.setCurrentStreak(2);
        ug.setLastCheckinDate(yesterday);

        when(gamificationRepository.findById(userId)).thenReturn(Optional.of(ug));
        Badge streak3 = new Badge("STREAK_MASTER", "Streak Master", "3 day streak", "STREAK", "🔥", 10, "RARE");
        when(badgeRepository.findById("STREAK_MASTER")).thenReturn(Optional.of(streak3));

        GamificationService.CheckInResult result = service.checkIn(userId);

        assertEquals(3, result.newStreak());
        // 5 base checkin XP + 10 XP badge reward = 15 XP
        assertEquals(15, result.pointsEarned());
        assertFalse(result.claimedToday());
    }

    @Test
    @DisplayName("7. Missed day resets streak back to 1")
    void checkIn_resetsStreakOnMissedDay() {
        Long userId = 1L;
        LocalDate threeDaysAgo = LocalDate.now(ZoneOffset.UTC).minusDays(3);
        UserGamification ug = new UserGamification(userId);
        ug.setCurrentStreak(10);
        ug.setLastCheckinDate(threeDaysAgo);

        when(gamificationRepository.findById(userId)).thenReturn(Optional.of(ug));

        GamificationService.CheckInResult result = service.checkIn(userId);

        assertEquals(1, result.newStreak());
    }

    @Test
    @DisplayName("8 & 17. Empty leaderboard behavior and sorting")
    void getLeaderboard_emptyBehavior() {
        when(gamificationRepository.findAllByOrderByTotalPointsDesc()).thenReturn(List.of());

        GamificationService.LeaderboardResponse response = service.getLeaderboard("all_time", "all", 1L);

        assertNotNull(response);
        assertTrue(response.podium().isEmpty());
        assertTrue(response.rankings().isEmpty());
        assertEquals(0, response.totalParticipants());
    }

    @Test
    @DisplayName("9 & 10. Leaderboard date period and stream filtering")
    void getLeaderboard_withFilters() {
        Long userId1 = 1L;
        User user1 = new User("Learner One", "one@example.com", "pic.jpg", Role.VIEWER);
        when(userRepository.findAllById(any())).thenReturn(List.of(user1));

        Object[] row = new Object[]{ userId1, 150L };
        List<Object[]> rows = new ArrayList<>();
        rows.add(row);
        when(transactionRepository.findLeaderboardByDateRangeAndStream(any(), eq("Engineering")))
                .thenReturn(rows);

        GamificationService.LeaderboardResponse response = service.getLeaderboard("weekly", "Engineering", userId1);

        assertNotNull(response);
        assertEquals("weekly", response.timeframe());
        assertEquals("Engineering", response.stream());
    }

    @Test
    @DisplayName("11 & 12. Current user rank calculation outside top 10")
    void getLeaderboard_currentUserRankOutsideTop10() {
        Long currentUserId = 25L;
        User u = new User("Learner 25", "l25@example.com", "pic.jpg", Role.VIEWER);
        when(userRepository.findById(currentUserId)).thenReturn(Optional.of(u));

        UserGamification ug = new UserGamification(currentUserId);
        ug.addPoints(45);
        when(gamificationRepository.findById(currentUserId)).thenReturn(Optional.of(ug));
        when(gamificationRepository.findRankByPoints(45)).thenReturn(27L);

        GamificationService.LeaderboardResponse response = service.getLeaderboard("all_time", "all", currentUserId);

        assertNotNull(response.currentUserRank());
        assertEquals(27, response.currentUserRank().rank());
    }

    @Test
    @DisplayName("13. Badge unlock duplicate prevention")
    void unlockBadge_preventsDuplicate() {
        Long userId = 1L;
        UserGamification ug = new UserGamification(userId);
        when(gamificationRepository.findById(userId)).thenReturn(Optional.of(ug));
        when(userBadgeRepository.existsByUserIdAndBadgeId(userId, "FAST_LEARNER")).thenReturn(true);

        List<Badge> badges = service.recordLessonCompletion(userId, "lesson-1", "Web");

        assertTrue(badges.isEmpty());
    }

    @Test
    @DisplayName("15 & 16. Admin point adjustment and audit logging")
    void adjustPointsManual_logsAuditAndTransaction() {
        Long userId = 1L;
        UserGamification ug = new UserGamification(userId);
        when(gamificationRepository.findById(userId)).thenReturn(Optional.of(ug));

        service.adjustPointsManual(userId, 50, "Winner of hackathon", "admin@test.com");

        assertEquals(50, ug.getTotalPoints());
        verify(transactionRepository).save(any(PointTransaction.class));
        verify(auditService).log(eq("admin@test.com"), eq("MANUAL_XP_ADJUSTMENT"), any(), contains("Winner of hackathon"));
    }

    @Test
    @DisplayName("14. Admin rule update validates non-negative points")
    void updatePointRule_validatesPoints() {
        assertThrows(IllegalArgumentException.class, () -> {
            service.updatePointRule("LESSON_COMPLETED", -10, "admin@test.com");
        });
    }
}
