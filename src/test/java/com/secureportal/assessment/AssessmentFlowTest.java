package com.secureportal.assessment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureportal.course.CourseRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static com.secureportal.testsupport.TestPrincipals.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Quizzes, assessments, gating, hidden answers, timing and grading, through the real API and database. */
@SpringBootTest(properties = {"storage.provider=local", "storage.local-path=target/assessment-test-storage"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*local.*")
class AssessmentFlowTest {

    private static final String ADMIN = "assess-flow-admin@example.com";
    private static final String LEARNER = "assess-flow-learner@example.com";
    private static final String OTHER = "assess-flow-other@example.com";
    private static final String RIGHT = "The right answer";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private AttemptRepository attemptRepository;
    @Autowired
    private AssessmentRepository assessmentRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper objectMapper;

    private User admin;
    private User learner;
    private User other;

    /** A published course: module 1 (two lessons, 9 approved questions) and module 2 (one lesson). */
    private record Fixture(String course, String module1, String module2, String lesson1, String lesson2, String lesson3) {
    }

    @BeforeEach
    void users() {
        admin = user(ADMIN, Role.ADMIN);
        learner = user(LEARNER, Role.VIEWER);
        other = user(OTHER, Role.VIEWER);
    }

    @AfterEach
    void cleanUp() {
        courseRepository.deleteAll();
        for (String email : List.of(ADMIN, LEARNER, OTHER)) {
            userRepository.findByEmailIgnoreCase(email).ifPresent(userRepository::delete);
        }
    }

    @Test
    void adminConfiguresQuizzesAssessmentsAndAFinalWithValidation() throws Exception {
        Fixture f = fixture();

        mockMvc.perform(put("/api/admin/modules/" + f.module1 + "/assessment").contentType(MediaType.APPLICATION_JSON)
                        .content(config("QUIZ", "Practice", 2, 2, 1, 80, 10, 3, true)).with(csrf()).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("QUIZ"))
                .andExpect(jsonPath("$.questionCount").value(5))
                .andExpect(jsonPath("$.passPercent").doesNotExist())
                .andExpect(jsonPath("$.timeLimitMinutes").doesNotExist())
                .andExpect(jsonPath("$.gatesNext").value(false))
                .andExpect(jsonPath("$.availableEasy").value(4))
                .andExpect(jsonPath("$.availableMedium").value(3))
                .andExpect(jsonPath("$.availableHard").value(2));

        mockMvc.perform(put("/api/admin/modules/" + f.module1 + "/assessment").contentType(MediaType.APPLICATION_JSON)
                        .content(config("ASSESSMENT", "Module 1 test", 2, 2, 1, 70, 30, 2, true)).with(csrf()).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("ASSESSMENT"))
                .andExpect(jsonPath("$.passPercent").value(70))
                .andExpect(jsonPath("$.timeLimitMinutes").value(30))
                .andExpect(jsonPath("$.maxAttempts").value(2))
                .andExpect(jsonPath("$.gatesNext").value(true));
        assertThat(assessmentRepository.count()).as("saving again updates, not duplicates").isEqualTo(1);

        mockMvc.perform(put("/api/admin/courses/" + f.course + "/final-assessment").contentType(MediaType.APPLICATION_JSON)
                        .content(config("ASSESSMENT", "Final exam", 3, 3, 2, 60, null, null, true)).with(csrf()).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gatesNext").value(false))
                .andExpect(jsonPath("$.moduleId").doesNotExist());

        mockMvc.perform(get("/api/admin/courses/" + f.course).with(as(admin)))
                .andExpect(jsonPath("$.modules[0].assessment.title").value("Module 1 test"))
                .andExpect(jsonPath("$.modules[1].assessment").doesNotExist())
                .andExpect(jsonPath("$.finalAssessment.title").value("Final exam"));

        for (String bad : List.of(config("QUIZ", "Empty", 0, 0, 0, null, null, null, false),
                config("ASSESSMENT", "No pass mark", 1, 0, 0, null, null, null, false),
                config("ASSESSMENT", "Pass zero", 1, 0, 0, 0, null, null, false),
                config("ASSESSMENT", "Over 100", 1, 0, 0, 101, null, null, false),
                config("ASSESSMENT", "Zero minutes", 1, 0, 0, 50, 0, null, false),
                config("ASSESSMENT", "Zero attempts", 1, 0, 0, 50, null, 0, false))) {
            mockMvc.perform(put("/api/admin/modules/" + f.module2 + "/assessment").contentType(MediaType.APPLICATION_JSON)
                            .content(bad).with(csrf()).with(as(admin)))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(delete("/api/admin/modules/" + f.module1 + "/assessment").with(csrf()).with(as(admin)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/admin/courses/" + f.course + "/final-assessment").with(csrf()).with(as(admin)))
                .andExpect(status().isNoContent());
        assertThat(assessmentRepository.count()).isZero();
    }

    @Test
    void aGatingAssessmentLocksUntilLessonsAreDoneThenGradesOnTheServerAndUnlocksTheNextModule() throws Exception {
        Fixture f = fixture();
        String assessment = saveModuleAssessment(f.module1, config("ASSESSMENT", "Module 1 test", 2, 2, 1, 50, null, 2, true));
        mockMvc.perform(post("/api/courses/" + f.course + "/enroll").with(csrf()).with(as(learner))).andExpect(status().isOk());

        mockMvc.perform(get("/api/courses/" + f.course).with(as(learner)))
                .andExpect(jsonPath("$.modules[0].assessment.status").value("LOCKED"))
                .andExpect(jsonPath("$.modules[0].assessment.lockedReason").value(containsString("Complete all lessons")))
                .andExpect(jsonPath("$.modules[1].locked").value(true))
                .andExpect(jsonPath("$.modules[1].lockedReason").value(containsString("Pass")));
        mockMvc.perform(post("/api/assessments/" + assessment + "/attempts").with(csrf()).with(as(learner)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/courses/" + f.course + "/lessons/" + f.lesson3).with(as(learner)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(containsString("Pass")));

        complete(f, f.lesson1);
        complete(f, f.lesson2);
        mockMvc.perform(get("/api/courses/" + f.course).with(as(learner)))
                .andExpect(jsonPath("$.modules[0].assessment.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.modules[0].assessment.attemptsLeft").value(2));

        MvcResult started = mockMvc.perform(post("/api/assessments/" + assessment + "/attempts").with(csrf()).with(as(learner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.totalCount").value(5))
                .andExpect(jsonPath("$.questions.length()").value(5))
                .andExpect(jsonPath("$.questions[0].options.length()").value(4))
                .andExpect(jsonPath("$.questions[0].correct").doesNotExist())
                .andExpect(jsonPath("$.questions[0].correctIndex").doesNotExist())
                .andExpect(jsonPath("$.questions[0].explanation").doesNotExist())
                .andExpect(jsonPath("$.secondsLeft").doesNotExist())
                .andReturn();
        JsonNode attempt = json(started);
        String attemptId = attempt.get("id").asText();

        mockMvc.perform(post("/api/assessments/" + assessment + "/attempts").with(csrf()).with(as(learner)))
                .andExpect(jsonPath("$.id").value(attemptId));
        mockMvc.perform(get("/api/attempts/" + attemptId).with(as(other))).andExpect(status().isNotFound());

        // Answer 3 of 5 correctly (60% against a 50% pass mark); a running attempt still reveals nothing.
        JsonNode questions = attempt.get("questions");
        for (int i = 0; i < questions.size(); i++) {
            int choice = i < 3 ? indexOf(questions.get(i), RIGHT) : indexOfOther(questions.get(i), RIGHT);
            mockMvc.perform(put("/api/attempts/" + attemptId + "/answers").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"questionId\":\"" + questions.get(i).get("id").asText() + "\",\"optionIndex\":" + choice + "}")
                            .with(csrf()).with(as(learner)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.questions[0].correct").doesNotExist())
                    .andExpect(jsonPath("$.questions[" + i + "].selectedIndex").value(choice));
        }

        MvcResult done = mockMvc.perform(post("/api/attempts/" + attemptId + "/submit").with(csrf()).with(as(learner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.scorePercent").value(60))
                .andExpect(jsonPath("$.correctCount").value(3))
                .andExpect(jsonPath("$.passed").value(true))
                .andExpect(jsonPath("$.questions[0].explanation").exists())
                .andReturn();
        JsonNode reviewed = json(done).get("questions");
        for (int i = 0; i < reviewed.size(); i++) {
            assertThat(reviewed.get(i).get("options").get(reviewed.get(i).get("correctIndex").asInt()).asText()).isEqualTo(RIGHT);
            assertThat(reviewed.get(i).get("correct").asBoolean()).isEqualTo(i < 3);
        }
        mockMvc.perform(post("/api/attempts/" + attemptId + "/submit").with(csrf()).with(as(learner)))
                .andExpect(jsonPath("$.scorePercent").value(60));
        mockMvc.perform(put("/api/attempts/" + attemptId + "/answers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"" + questions.get(0).get("id").asText() + "\",\"optionIndex\":0}")
                        .with(csrf()).with(as(learner)))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/courses/" + f.course).with(as(learner)))
                .andExpect(jsonPath("$.modules[0].assessment.status").value("PASSED"))
                .andExpect(jsonPath("$.modules[0].assessment.bestScore").value(60))
                .andExpect(jsonPath("$.modules[1].locked").value(false));
        mockMvc.perform(get("/api/courses/" + f.course + "/lessons/" + f.lesson3).with(as(learner))).andExpect(status().isOk());
        mockMvc.perform(get("/api/assessments/" + assessment + "/attempts").with(as(learner)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].passed").value(true));

        mockMvc.perform(get("/api/admin/courses/" + f.course + "/assessment-results").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assessments[0].attempts").value(1))
                .andExpect(jsonPath("$.assessments[0].learners").value(1))
                .andExpect(jsonPath("$.assessments[0].averageScore").value(60))
                .andExpect(jsonPath("$.assessments[0].passRatePercent").value(100))
                .andExpect(jsonPath("$.hardestQuestions.length()").value(5));
        mockMvc.perform(get("/api/admin/courses/" + f.course + "/assessment-results").with(as(learner)))
                .andExpect(status().isForbidden());
    }

    @Test
    void aQuizGivesInstantFeedbackAndAllowsUnlimitedRetakes() throws Exception {
        Fixture f = fixture();
        String quiz = saveModuleAssessment(f.module1, config("QUIZ", "Practice", 2, 1, 0, null, null, null, false));
        mockMvc.perform(post("/api/courses/" + f.course + "/enroll").with(csrf()).with(as(learner))).andExpect(status().isOk());
        complete(f, f.lesson1);
        complete(f, f.lesson2);

        JsonNode attempt = json(mockMvc.perform(post("/api/assessments/" + quiz + "/attempts").with(csrf()).with(as(learner)))
                .andExpect(status().isOk()).andReturn());
        JsonNode first = attempt.get("questions").get(0);
        int wrong = indexOfOther(first, RIGHT);

        mockMvc.perform(put("/api/attempts/" + attempt.get("id").asText() + "/answers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"" + first.get("id").asText() + "\",\"optionIndex\":" + wrong + "}")
                        .with(csrf()).with(as(learner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].correct").value(false))
                .andExpect(jsonPath("$.questions[0].correctIndex").exists())
                .andExpect(jsonPath("$.questions[0].explanation").exists())
                .andExpect(jsonPath("$.questions[1].correct").doesNotExist());

        mockMvc.perform(post("/api/attempts/" + attempt.get("id").asText() + "/submit").with(csrf()).with(as(learner)))
                .andExpect(jsonPath("$.passed").doesNotExist())
                .andExpect(jsonPath("$.passPercent").doesNotExist());
        for (int i = 0; i < 3; i++) {
            MvcResult again = mockMvc.perform(post("/api/assessments/" + quiz + "/attempts").with(csrf()).with(as(learner)))
                    .andExpect(status().isOk()).andReturn();
            mockMvc.perform(post("/api/attempts/" + json(again).get("id").asText() + "/submit").with(csrf()).with(as(learner)))
                    .andExpect(status().isOk());
        }
        assertThat(attemptRepository.countByUserIdAndAssessmentId(learner.getId(), java.util.UUID.fromString(quiz))).isEqualTo(4);
    }

    @Test
    void aTimedAssessmentIsGradedAutomaticallyWhenTheTimeRunsOut() throws Exception {
        Fixture f = fixture();
        String timed = saveModuleAssessment(f.module1, config("ASSESSMENT", "Timed", 2, 1, 0, 50, 1, null, false));
        mockMvc.perform(post("/api/courses/" + f.course + "/enroll").with(csrf()).with(as(learner))).andExpect(status().isOk());
        complete(f, f.lesson1);
        complete(f, f.lesson2);

        JsonNode attempt = json(mockMvc.perform(post("/api/assessments/" + timed + "/attempts").with(csrf()).with(as(learner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.secondsLeft").isNumber()).andReturn());
        String attemptId = attempt.get("id").asText();
        JsonNode q = attempt.get("questions").get(0);
        mockMvc.perform(put("/api/attempts/" + attemptId + "/answers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"" + q.get("id").asText() + "\",\"optionIndex\":" + indexOf(q, RIGHT) + "}")
                        .with(csrf()).with(as(learner)))
                .andExpect(status().isOk());

        jdbc.update("UPDATE attempts SET expires_at = ? WHERE id = UNHEX(?)",
                Timestamp.from(Instant.now().minus(2, ChronoUnit.DAYS)), attemptId.replace("-", ""));

        mockMvc.perform(put("/api/attempts/" + attemptId + "/answers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"" + q.get("id").asText() + "\",\"optionIndex\":0}").with(csrf()).with(as(learner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(containsString("Time's up")));
        mockMvc.perform(get("/api/attempts/" + attemptId).with(as(learner)))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.timedOut").value(true))
                .andExpect(jsonPath("$.correctCount").value(1))
                .andExpect(jsonPath("$.scorePercent").value(33));
    }

    @Test
    void anAttemptLimitIsEnforcedAndTheFinalAssessmentWaitsForEveryLesson() throws Exception {
        Fixture f = fixture();
        String limited = saveModuleAssessment(f.module1, config("ASSESSMENT", "One shot", 1, 0, 0, 100, null, 1, false));
        String finalExam = json(mockMvc.perform(put("/api/admin/courses/" + f.course + "/final-assessment")
                        .contentType(MediaType.APPLICATION_JSON).content(config("QUIZ", "Course quiz", 2, 2, 1, null, null, null, false))
                        .with(csrf()).with(as(admin))).andExpect(status().isOk()).andReturn()).get("id").asText();
        mockMvc.perform(post("/api/courses/" + f.course + "/enroll").with(csrf()).with(as(learner))).andExpect(status().isOk());
        complete(f, f.lesson1);
        complete(f, f.lesson2);

        JsonNode attempt = json(mockMvc.perform(post("/api/assessments/" + limited + "/attempts").with(csrf()).with(as(learner)))
                .andExpect(status().isOk()).andReturn());
        mockMvc.perform(post("/api/attempts/" + attempt.get("id").asText() + "/submit").with(csrf()).with(as(learner)))
                .andExpect(jsonPath("$.passed").value(false)).andExpect(jsonPath("$.scorePercent").value(0));

        mockMvc.perform(post("/api/assessments/" + limited + "/attempts").with(csrf()).with(as(learner)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(containsString("used all")));
        mockMvc.perform(get("/api/courses/" + f.course).with(as(learner)))
                .andExpect(jsonPath("$.modules[0].assessment.status").value("EXHAUSTED"))
                .andExpect(jsonPath("$.finalAssessment.status").value("LOCKED"))
                .andExpect(jsonPath("$.finalAssessment.lockedReason").value(containsString("every lesson")));

        complete(f, f.lesson3);
        mockMvc.perform(get("/api/courses/" + f.course).with(as(learner)))
                .andExpect(jsonPath("$.finalAssessment.status").value("AVAILABLE"));
        mockMvc.perform(post("/api/assessments/" + finalExam + "/attempts").with(csrf()).with(as(learner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.questions.length()").value(5));
    }

    @Test
    void viewersCannotConfigureAssessmentsAndDeletingAModuleTakesItsAssessmentAndAttemptsWithIt() throws Exception {
        Fixture f = fixture();
        mockMvc.perform(put("/api/admin/modules/" + f.module1 + "/assessment").contentType(MediaType.APPLICATION_JSON)
                        .content(config("QUIZ", "Nope", 1, 0, 0, null, null, null, false)).with(csrf()).with(as(learner)))
                .andExpect(status().isForbidden());

        String quiz = saveModuleAssessment(f.module1, config("QUIZ", "Practice", 1, 1, 0, null, null, null, false));
        mockMvc.perform(post("/api/courses/" + f.course + "/enroll").with(csrf()).with(as(learner))).andExpect(status().isOk());
        complete(f, f.lesson1);
        complete(f, f.lesson2);
        mockMvc.perform(post("/api/assessments/" + quiz + "/attempts").with(csrf()).with(as(learner))).andExpect(status().isOk());
        assertThat(attemptRepository.count()).isEqualTo(1);

        mockMvc.perform(delete("/api/admin/modules/" + f.module1).with(csrf()).with(as(admin))).andExpect(status().isNoContent());
        assertThat(assessmentRepository.count()).isZero();
        assertThat(attemptRepository.count()).isZero();
    }

    // ---- helpers ----

    private Fixture fixture() throws Exception {
        MockMultipartHttpServletRequestBuilder course = multipart("/api/admin/courses");
        course.param("title", "Assessment course");
        String courseId = json(perform(course).andReturn()).get("id").asText();
        String module1 = addModule(courseId, "Foundations");
        String module2 = addModule(courseId, "Practice");
        String lesson1 = addLesson(module1, "Lesson one");
        String lesson2 = addLesson(module1, "Lesson two");
        String lesson3 = addLesson(module2, "Lesson three");
        int[] easy = {2, 2, 0}; int[] medium = {1, 2, 0}; int[] hard = {1, 1, 0};
        addQuestions(lesson1, "EASY", easy[0]); addQuestions(lesson2, "EASY", easy[1]);
        addQuestions(lesson1, "MEDIUM", medium[0]); addQuestions(lesson2, "MEDIUM", medium[1]);
        addQuestions(lesson1, "HARD", hard[0]); addQuestions(lesson2, "HARD", hard[1]);
        mockMvc.perform(post("/api/admin/courses/" + courseId + "/publish").with(csrf()).with(as(admin))).andExpect(status().isOk());
        return new Fixture(courseId, module1, module2, lesson1, lesson2, lesson3);
    }

    private void addQuestions(String lessonId, String difficulty, int count) throws Exception {
        for (int i = 0; i < count; i++) {
            mockMvc.perform(post("/api/admin/lessons/" + lessonId + "/questions").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"text\":\"" + difficulty + " question " + lessonId.substring(0, 8) + "-" + i + "?\",\"difficulty\":\"" + difficulty
                                    + "\",\"explanation\":\"Because.\",\"options\":[\"" + RIGHT + "\",\"Wrong one\",\"Wrong two\",\"Wrong three\"],\"correctIndex\":0}")
                            .with(csrf()).with(as(admin)))
                    .andExpect(status().isOk());
        }
    }

    private String saveModuleAssessment(String moduleId, String body) throws Exception {
        return json(mockMvc.perform(put("/api/admin/modules/" + moduleId + "/assessment").contentType(MediaType.APPLICATION_JSON)
                .content(body).with(csrf()).with(as(admin))).andExpect(status().isOk()).andReturn()).get("id").asText();
    }

    private void complete(Fixture f, String lessonId) throws Exception {
        mockMvc.perform(put("/api/courses/" + f.course + "/lessons/" + lessonId + "/progress").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionSeconds\":10,\"completed\":true}").with(csrf()).with(as(learner)))
                .andExpect(status().isOk());
    }

    private static String config(String type, String title, int easy, int medium, int hard, Integer pass, Integer minutes,
                                 Integer attempts, boolean gates) {
        return "{\"type\":\"" + type + "\",\"title\":\"" + title + "\",\"easyCount\":" + easy + ",\"mediumCount\":" + medium
                + ",\"hardCount\":" + hard + ",\"passPercent\":" + pass + ",\"timeLimitMinutes\":" + minutes
                + ",\"maxAttempts\":" + attempts + ",\"gatesNext\":" + gates + "}";
    }

    private static int indexOf(JsonNode question, String text) {
        for (int i = 0; i < question.get("options").size(); i++) {
            if (question.get("options").get(i).asText().equals(text)) {
                return i;
            }
        }
        throw new AssertionError("option not found: " + text);
    }

    private static int indexOfOther(JsonNode question, String text) {
        return indexOf(question, text) == 0 ? 1 : 0;
    }

    private String addModule(String courseId, String title) throws Exception {
        return json(mockMvc.perform(post("/api/admin/courses/" + courseId + "/modules").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + title + "\"}").with(csrf()).with(as(admin))).andExpect(status().isOk()).andReturn()).get("id").asText();
    }

    private String addLesson(String moduleId, String title) throws Exception {
        byte[] head = {0x00, 0x00, 0x00, 0x1C, 'f', 't', 'y', 'p', 'm', 'p', '4', '2',
                0x00, 0x00, 0x00, 0x00, 'm', 'p', '4', '2', 'i', 's', 'o', 'm', 'a', 'v', 'c', '1'};
        MockMultipartHttpServletRequestBuilder lesson = multipart("/api/admin/modules/" + moduleId + "/lessons");
        lesson.file(new MockMultipartFile("video", "lecture.mp4", "video/mp4", head));
        lesson.param("title", title);
        return json(perform(lesson).andReturn()).get("id").asText();
    }

    private ResultActions perform(MockMultipartHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(csrf()).with(as(admin))).andExpect(status().isOk());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private User user(String email, Role role) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> userRepository.save(new User(email, "Assessment Flow Test", null, role)));
    }
}
