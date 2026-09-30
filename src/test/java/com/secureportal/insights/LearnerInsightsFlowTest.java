package com.secureportal.insights;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureportal.assessment.Assessment;
import com.secureportal.assessment.AssessmentRepository;
import com.secureportal.assessment.Attempt;
import com.secureportal.assessment.AttemptRepository;
import com.secureportal.assessment.AttemptQuestion;
import com.secureportal.assessment.AttemptQuestionRepository;
import com.secureportal.course.CourseRepository;
import com.secureportal.course.EnrollmentRepository;
import com.secureportal.quiz.Difficulty;
import com.secureportal.quiz.Question;
import com.secureportal.quiz.QuestionOption;
import com.secureportal.quiz.QuestionRepository;
import com.secureportal.quiz.QuestionSource;
import com.secureportal.quiz.QuestionStatus;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.time.Instant;

import static com.secureportal.testsupport.TestPrincipals.as;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The learner dashboard reads course completion, watch time, quiz scores and interview performance without
 *  writing anything of its own — this exercises the real completion/watch-time tracking end to end, and
 *  seeds a graded attempt directly (the taking-a-quiz flow itself is covered elsewhere). */
@SpringBootTest(properties = {"storage.provider=local", "storage.local-path=target/insights-test-storage"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*local.*")
class LearnerInsightsFlowTest {

    private static final String ADMIN_EMAIL = "insights-flow-admin@example.com";
    private static final String VIEWER_EMAIL = "insights-flow-viewer@example.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private EnrollmentRepository enrollmentRepository;
    @Autowired
    private AssessmentRepository assessmentRepository;
    @Autowired
    private AttemptRepository attemptRepository;
    @Autowired
    private QuestionRepository questionRepository;
    @Autowired
    private AttemptQuestionRepository attemptQuestionRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private User admin;
    private User viewer;

    @BeforeEach
    void users() {
        admin = user(ADMIN_EMAIL, Role.ADMIN);
        viewer = user(VIEWER_EMAIL, Role.VIEWER);
    }

    @AfterEach
    void cleanUp() {
        attemptQuestionRepository.deleteAll();
        attemptRepository.deleteAll();
        questionRepository.deleteAll();
        courseRepository.deleteAll();
        userRepository.findByEmailIgnoreCase(ADMIN_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmailIgnoreCase(VIEWER_EMAIL).ifPresent(userRepository::delete);
    }

    @Test
    void completingACourseTakingAQuizAndHavingNoInterviewsYetAllShowUpCorrectly() throws Exception {
        // Empty state, before anything happened.
        mockMvc.perform(get("/api/me/insights").with(as(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coursesEnrolled").value(0))
                .andExpect(jsonPath("$.interviews.sessionsCompleted").value(0))
                .andExpect(jsonPath("$.interviews.strongest").doesNotExist())
                .andExpect(jsonPath("$.quizzes.attemptCount").value(0));

        // Build and publish a one-lesson course, category "Cloud".
        MockMultipartHttpServletRequestBuilder courseRequest = multipart("/api/admin/courses");
        courseRequest.param("title", "Kubernetes for platform teams");
        courseRequest.param("category", "Cloud");
        String courseId = json(mockMvc.perform(courseRequest.with(csrf()).with(as(admin)))
                .andExpect(status().isOk()).andReturn()).get("id").asText();
        String moduleId = json(mockMvc.perform(post("/api/admin/courses/" + courseId + "/modules")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"M\"}")
                        .with(csrf()).with(as(admin))).andReturn()).get("id").asText();
        String lessonId = json(mockMvc.perform(multipart("/api/admin/modules/" + moduleId + "/lessons")
                        .file(new org.springframework.mock.web.MockMultipartFile("video", "l.mp4", "video/mp4", mp4()))
                        .param("title", "L").with(csrf()).with(as(admin)))
                .andExpect(status().isOk()).andReturn()).get("id").asText();
        mockMvc.perform(post("/api/admin/courses/" + courseId + "/publish").with(csrf()).with(as(admin)))
                .andExpect(status().isOk());

        // Enroll, watch some of it (two forward saves), then finish it.
        mockMvc.perform(post("/api/courses/" + courseId + "/enroll").with(csrf()).with(as(viewer)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/courses/" + courseId + "/lessons/" + lessonId + "/progress")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"positionSeconds\":12,\"completed\":false}")
                        .with(csrf()).with(as(viewer)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/courses/" + courseId + "/lessons/" + lessonId + "/progress")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"positionSeconds\":30,\"completed\":true}")
                        .with(csrf()).with(as(viewer)))
                .andExpect(status().isOk());

        // A graded quiz attempt for that course, seeded directly (the taking-a-quiz path is tested elsewhere).
        String assessmentBody = "{\"type\":\"QUIZ\",\"title\":\"Module check\",\"easyCount\":1,\"mediumCount\":0,\"hardCount\":0,"
                + "\"gatesNext\":false}";
        mockMvc.perform(put("/api/admin/modules/" + moduleId + "/assessment").contentType(MediaType.APPLICATION_JSON)
                        .content(assessmentBody).with(csrf()).with(as(admin)))
                .andExpect(status().isOk());
        Assessment assessment = assessmentRepository.findByModuleId(java.util.UUID.fromString(moduleId)).orElseThrow();
        Attempt attempt = new Attempt(assessment.getId(), viewer.getId(), 5, Instant.now().plusSeconds(600));
        attempt.grade(4, 5, 80, true, false);
        attemptRepository.save(attempt);

        // One easy question answered right, one hard question answered wrong — real material for the
        // "how do you do on hard questions specifically" breakdown.
        java.util.UUID lessonUuid = java.util.UUID.fromString(lessonId);
        Question easy = questionRepository.save(new Question(java.util.UUID.fromString(courseId), lessonUuid, "Easy one",
                Difficulty.EASY, null, null, QuestionSource.MANUAL, QuestionStatus.APPROVED,
                java.util.List.of(new QuestionOption("A", true), new QuestionOption("B", false)), null));
        Question hard = questionRepository.save(new Question(java.util.UUID.fromString(courseId), lessonUuid, "Hard one",
                Difficulty.HARD, null, null, QuestionSource.MANUAL, QuestionStatus.APPROVED,
                java.util.List.of(new QuestionOption("A", true), new QuestionOption("B", false)), null));
        AttemptQuestion easyAnswer = new AttemptQuestion(attempt.getId(), 0, easy.getId(), "0,1");
        easyAnswer.answer(0, true);
        AttemptQuestion hardAnswer = new AttemptQuestion(attempt.getId(), 1, hard.getId(), "0,1");
        hardAnswer.answer(1, false);
        attemptQuestionRepository.save(easyAnswer);
        attemptQuestionRepository.save(hardAnswer);

        mockMvc.perform(get("/api/me/insights").with(as(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coursesEnrolled").value(1))
                .andExpect(jsonPath("$.coursesCompleted").value(1))
                .andExpect(jsonPath("$.overallCompletionPercent").value(100))
                .andExpect(jsonPath("$.courses[0].title").value("Kubernetes for platform teams"))
                .andExpect(jsonPath("$.courses[0].completed").value(true))
                .andExpect(jsonPath("$.courses[0].completedLessons").value(1))
                .andExpect(jsonPath("$.courses[0].totalLessons").value(1))
                .andExpect(jsonPath("$.totalWatchSeconds").value(30)) // 12 (0->12) + 18 (12->30)
                .andExpect(jsonPath("$.quizzes.attemptCount").value(1))
                .andExpect(jsonPath("$.quizzes.averageScore").value(80.0))
                .andExpect(jsonPath("$.quizzes.byCategory[0].category").value("Cloud"))
                .andExpect(jsonPath("$.quizzes.byCategory[0].averageScore").value(80.0))
                .andExpect(jsonPath("$.quizzes.byDifficulty[0].difficulty").value("EASY"))
                .andExpect(jsonPath("$.quizzes.byDifficulty[0].accuracyPercent").value(100.0))
                .andExpect(jsonPath("$.quizzes.byDifficulty[1].difficulty").value("HARD"))
                .andExpect(jsonPath("$.quizzes.byDifficulty[1].accuracyPercent").value(0.0))
                .andExpect(jsonPath("$.quizzes.recentTrend").doesNotExist()) // only one attempt — too little to call a trend
                .andExpect(jsonPath("$.quizzes.recent[0].scorePercent").value(80))
                .andExpect(jsonPath("$.quizzes.recent[0].courseTitle").value("Kubernetes for platform teams"))
                .andExpect(jsonPath("$.interviews.sessionsCompleted").value(0))
                .andExpect(jsonPath("$.interviews.byQuestionCategory").isEmpty())
                .andExpect(jsonPath("$.interviews.latestTopFix").doesNotExist())
                .andExpect(jsonPath("$.gamification").exists());

        // An admin never has a personal dashboard — the endpoint still answers cleanly, all zeroed.
        mockMvc.perform(get("/api/me/insights").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coursesEnrolled").value(0));
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private User user(String email, Role role) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> userRepository.save(new User(email, "Insights Flow Test", null, role)));
    }

    private static byte[] mp4() {
        byte[] head = {0x00, 0x00, 0x00, 0x1C, 'f', 't', 'y', 'p', 'm', 'p', '4', '2',
                0x00, 0x00, 0x00, 0x00, 'm', 'p', '4', '2', 'i', 's', 'o', 'm', 'a', 'v', 'c', '1'};
        byte[] bytes = new byte[4096];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i % 251);
        }
        System.arraycopy(head, 0, bytes, 0, head.length);
        return bytes;
    }
}
