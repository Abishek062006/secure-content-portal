package com.secureportal.gamification;

import com.secureportal.audit.AuditRepository;
import com.secureportal.user.Role;
import com.secureportal.user.User;
import com.secureportal.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.secureportal.testsupport.TestPrincipals.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Points can't be farmed or forged: each award pays once per source however often it is triggered (even simultaneously),
 * admins never take part, and the admin controls are bounded, validated and audited.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*local.*")
class GamificationFlowTest {

    private static final String ADMIN = "gamification-admin@example.com";
    private static final String ONE = "gamification-one@example.com";
    private static final String TWO = "gamification-two@example.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private GamificationService gamification;
    @Autowired
    private UserGamificationRepository gamificationRepository;
    @Autowired
    private AuditRepository auditRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private User admin;
    private User one;
    private User two;

    @BeforeEach
    void users() {
        admin = user(ADMIN, Role.ADMIN);
        one = user(ONE, Role.VIEWER);
        two = user(TWO, Role.VIEWER);
    }

    @AfterEach
    void cleanUp() {
        // Deleting the users removes their points, badges and history with them.
        for (String email : List.of(ADMIN, ONE, TWO)) {
            userRepository.findByEmailIgnoreCase(email).ifPresent(userRepository::delete);
        }
    }

    private User user(String email, Role role) {
        return userRepository.findByEmailIgnoreCase(email).orElseGet(() -> userRepository.save(new User(email, email, null, role)));
    }

    private int points(User user) {
        return gamification.summary(user.getId()).totalPoints();
    }

    @Test
    void lessonsQuizzesAndCoursesPayOnlyOncePerSource() {
        gamification.recordLessonCompletion(one.getId(), "lesson-a", "Web");
        int afterLesson = points(one);
        assertThat(afterLesson).isGreaterThanOrEqualTo(10);
        gamification.recordLessonCompletion(one.getId(), "lesson-a", "Web");
        assertThat(points(one)).isEqualTo(afterLesson);
        gamification.recordLessonCompletion(one.getId(), "lesson-b", "Web");
        assertThat(points(one)).isGreaterThan(afterLesson);

        // The retake exploit: passing the same quiz again and again must not keep paying.
        gamification.recordQuizAttempt(one.getId(), "quiz-a", 90, true, null);
        int afterQuiz = points(one);
        for (int i = 0; i < 6; i++) {
            gamification.recordQuizAttempt(one.getId(), "quiz-a", 90, true, null);
        }
        assertThat(points(one)).isEqualTo(afterQuiz);
        gamification.recordQuizAttempt(one.getId(), "quiz-b", 90, true, null);
        assertThat(points(one)).isGreaterThan(afterQuiz);

        gamification.recordCourseCompletion(one.getId(), "course-a", "Web");
        int afterCourse = points(one);
        gamification.recordCourseCompletion(one.getId(), "course-a", "Web");
        assertThat(points(one)).isEqualTo(afterCourse);
    }

    @Test
    void aFailedQuizAttemptEarnsNothing() {
        gamification.recordQuizAttempt(one.getId(), "quiz-fail", 40, false, null);
        assertThat(points(one)).isZero();
    }

    @Test
    void simultaneousCheckInsPayOnce() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<GamificationService.CheckInResult>> attempts = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            attempts.add(pool.submit(() -> {
                go.await();
                return gamification.checkIn(one.getId());
            }));
        }
        go.countDown();
        long paid = 0;
        for (Future<GamificationService.CheckInResult> attempt : attempts) {
            if (!attempt.get().claimedToday()) {
                paid++;
            }
        }
        pool.shutdown();

        assertThat(paid).as("only one of four simultaneous check-ins is paid").isEqualTo(1);
        assertThat(points(one)).isEqualTo(gamification.pointsFor(PointAction.DAILY_CHECKIN));
        assertThat(gamification.summary(one.getId()).currentStreak()).isEqualTo(1);
    }

    @Test
    void checkInOverHttpIsOncePerDayAndForLearnersOnly() throws Exception {
        mockMvc.perform(post("/api/gamification/check-in").with(csrf()).with(as(one)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.claimedToday").value(false)).andExpect(jsonPath("$.newStreak").value(1));
        mockMvc.perform(post("/api/gamification/check-in").with(csrf()).with(as(one)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.claimedToday").value(true)).andExpect(jsonPath("$.pointsEarned").value(0));
        mockMvc.perform(get("/api/gamification/me").with(as(one)))
                .andExpect(jsonPath("$.checkedInToday").value(true)).andExpect(jsonPath("$.totalPoints").value(points(one)));

        mockMvc.perform(post("/api/gamification/check-in").with(csrf()).with(as(admin))).andExpect(status().isForbidden());
    }

    @Test
    void theLeaderboardRanksLearnersOnlyAndShowsEveryoneTheirOwnPlace() throws Exception {
        gamification.award(one.getId(), PointAction.LESSON_COMPLETED, 200, "test", "Web", "board-1");
        gamification.award(two.getId(), PointAction.LESSON_COMPLETED, 100, "test", "Web", "board-2");
        // An admin somehow holding the most points must still never appear.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            gamificationRepository.ensureRow(admin.getId());
            gamificationRepository.addPoints(admin.getId(), 99_999);
        });

        mockMvc.perform(get("/api/gamification/leaderboard").with(as(two)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(ADMIN))))
                .andExpect(jsonPath("$.currentUserRank.userId").value(two.getId()))
                .andExpect(jsonPath("$.currentUserRank.isCurrentUser").value(true))
                .andExpect(jsonPath("$.currentUserRank.points").value(points(two)));

        String board = mockMvc.perform(get("/api/gamification/leaderboard").with(as(two))).andReturn().getResponse().getContentAsString();
        assertThat(board).doesNotContain("\"userId\":" + admin.getId() + ",");
        assertThat(board.indexOf("\"userId\":" + one.getId())).isLessThan(board.indexOf("\"userId\":" + two.getId()));

        // The admin has no place of their own, and a period/stream view is summed from the ledger.
        mockMvc.perform(get("/api/gamification/leaderboard").with(as(admin))).andExpect(jsonPath("$.currentUserRank").doesNotExist());
        mockMvc.perform(get("/api/gamification/leaderboard?timeframe=weekly&stream=Web").with(as(two)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.timeframe").value("weekly")).andExpect(jsonPath("$.stream").value("Web"))
                .andExpect(jsonPath("$.currentUserRank.points").value(100));
    }

    @Test
    void adminAdjustmentsAreBoundedValidatedAuditedAndNotVisibleToOthers() throws Exception {
        String url = "/api/admin/gamification/adjust";
        String base = "{\"userId\":" + one.getId() + ",";

        mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(base + "\"amount\":50,\"reason\":\"Hackathon winner\"}")
                .with(csrf()).with(as(one))).andExpect(status().isForbidden());
        mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(base + "\"amount\":20000,\"reason\":\"Too much\"}")
                .with(csrf()).with(as(admin))).andExpect(status().isBadRequest());
        mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(base + "\"amount\":0,\"reason\":\"Nothing\"}")
                .with(csrf()).with(as(admin))).andExpect(status().isBadRequest());
        mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(base + "\"amount\":10,\"reason\":\"  \"}")
                .with(csrf()).with(as(admin))).andExpect(status().isBadRequest());
        mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("{\"userId\":987654321,\"amount\":10,\"reason\":\"No such learner\"}")
                .with(csrf()).with(as(admin))).andExpect(status().isBadRequest());
        mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":" + admin.getId() + ",\"amount\":10,\"reason\":\"Admins can't earn\"}")
                .with(csrf()).with(as(admin))).andExpect(status().isBadRequest());
        assertThat(points(one)).isZero();

        mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(base + "\"amount\":50,\"reason\":\"Hackathon winner\"}")
                .with(csrf()).with(as(admin))).andExpect(status().isNoContent());
        assertThat(points(one)).isEqualTo(50);
        // Taking away more than they have stops at zero.
        mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(base + "\"amount\":-500,\"reason\":\"Correction\"}")
                .with(csrf()).with(as(admin))).andExpect(status().isNoContent());
        assertThat(points(one)).isZero();

        assertThat(auditRepository.findAll()).anyMatch(a -> "MANUAL_XP_ADJUSTMENT".equals(a.getAction())
                && a.getDetail().contains("Hackathon winner") && ADMIN.equals(a.getActorEmail()));

        // The learner sees their own history without internal fields or the admin's identity; another learner sees none of it.
        mockMvc.perform(get("/api/points/history").with(as(one)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(content().string(containsString("Hackathon winner")))
                .andExpect(content().string(not(containsString("sourceId"))))
                .andExpect(content().string(not(containsString(ADMIN))));
        mockMvc.perform(get("/api/points/history").with(as(two))).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void pointRulesCanOnlyBeChangedWithinBoundsAndOnlyForRealRules() throws Exception {
        int original = gamification.pointsFor(PointAction.LESSON_COMPLETED);
        String url = "/api/admin/gamification/rules/";
        try {
            mockMvc.perform(put(url + "LESSON_COMPLETED").contentType(MediaType.APPLICATION_JSON).content("{\"points\":15}")
                    .with(csrf()).with(as(one))).andExpect(status().isForbidden());
            mockMvc.perform(put(url + "LESSON_COMPLETED").contentType(MediaType.APPLICATION_JSON).content("{\"points\":5000}")
                    .with(csrf()).with(as(admin))).andExpect(status().isBadRequest());
            mockMvc.perform(put(url + "LESSON_COMPLETED").contentType(MediaType.APPLICATION_JSON).content("{\"points\":-1}")
                    .with(csrf()).with(as(admin))).andExpect(status().isBadRequest());
            mockMvc.perform(put(url + "MADE_UP_ACTION").contentType(MediaType.APPLICATION_JSON).content("{\"points\":10}")
                    .with(csrf()).with(as(admin))).andExpect(status().isBadRequest());
            mockMvc.perform(put(url + "MANUAL_ADJUSTMENT").contentType(MediaType.APPLICATION_JSON).content("{\"points\":10}")
                    .with(csrf()).with(as(admin))).andExpect(status().isBadRequest());
            assertThat(gamification.pointsFor(PointAction.LESSON_COMPLETED)).isEqualTo(original);

            mockMvc.perform(put(url + "LESSON_COMPLETED").contentType(MediaType.APPLICATION_JSON).content("{\"points\":15}")
                    .with(csrf()).with(as(admin))).andExpect(status().isOk()).andExpect(jsonPath("$.points").value(15));
            assertThat(gamification.pointsFor(PointAction.LESSON_COMPLETED)).isEqualTo(15);
            assertThat(auditRepository.findAll()).anyMatch(a -> "UPDATE_POINT_RULE".equals(a.getAction()));
        } finally {
            gamification.updatePointRule("LESSON_COMPLETED", original, ADMIN);
        }
    }

    @Test
    void badgesOfAnUnknownMemberAreNotFound() throws Exception {
        mockMvc.perform(get("/api/gamification/users/987654321/badges").with(as(one))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/gamification/users/" + two.getId() + "/badges").with(as(one))).andExpect(status().isOk());
    }
}
