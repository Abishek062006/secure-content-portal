package com.secureportal.quiz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureportal.ai.AiException;
import com.secureportal.ai.AiNotConfiguredException;
import com.secureportal.ai.LlmClient;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;

import static com.secureportal.testsupport.TestPrincipals.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The question bank through the real API, with the AI model faked (no key or network needed). */
@SpringBootTest(properties = {"storage.provider=local", "storage.local-path=target/question-test-storage"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*local.*")
class QuestionBankFlowTest {

    private static final String ADMIN_EMAIL = "bank-flow-admin@example.com";
    private static final String VIEWER_EMAIL = "bank-flow-viewer@example.com";
    private static final String FOUR = "[\"A\",\"B\",\"C\",\"D\"]";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private QuestionRepository questionRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private LlmClient llmClient;

    private User admin;
    private User viewer;

    @BeforeEach
    void users() {
        admin = user(ADMIN_EMAIL, Role.ADMIN);
        viewer = user(VIEWER_EMAIL, Role.VIEWER);
    }

    @AfterEach
    void cleanUp() {
        courseRepository.deleteAll();
        userRepository.findByEmailIgnoreCase(ADMIN_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmailIgnoreCase(VIEWER_EMAIL).ifPresent(userRepository::delete);
    }

    @Test
    void generatesReviewsEditsApprovesAndDeletesAiQuestions() throws Exception {
        Ids ids = createLesson(true);
        when(llmClient.complete(anyString(), anyString())).thenReturn("{\"questions\":["
                + q("What signs a ticket?", "EASY", 0, 3) + ","
                + q("Why bind to the session?", "HARD", 1, 66) + ","
                + "{\"question\":\"Broken\",\"difficulty\":\"EASY\",\"options\":[\"only one\"],\"correctIndex\":0}]}");

        MvcResult generated = mockMvc.perform(post("/api/admin/lessons/" + ids.lesson + "/questions/generate")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"count\":5}").with(csrf()).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("DRAFT"))
                .andExpect(jsonPath("$[0].source").value("AI"))
                .andExpect(jsonPath("$[0].lessonTitle").value("Signed tickets"))
                .andReturn();
        JsonNode first = json(generated).get(0);
        String questionId = first.get("id").asText();
        assertThat(first.get("options")).hasSize(4).filteredOn(o -> o.get("correct").asBoolean()).hasSize(1);

        mockMvc.perform(post("/api/admin/questions/" + questionId + "/approve").with(csrf()).with(as(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        mockMvc.perform(put("/api/admin/questions/" + questionId).contentType(MediaType.APPLICATION_JSON)
                        .content(request("Edited?", "MEDIUM", "[\"W\",\"X\",\"Y\",\"Z\"]", 3)).with(csrf()).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Edited?"))
                .andExpect(jsonPath("$.difficulty").value("MEDIUM"))
                .andExpect(jsonPath("$.options[3].text").value("Z"))
                .andExpect(jsonPath("$.options[3].correct").value(true));
        mockMvc.perform(post("/api/admin/questions/" + questionId + "/unapprove").with(csrf()).with(as(admin)))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        mockMvc.perform(get("/api/admin/courses/" + ids.course + "/questions").with(as(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));

        // Asking again must not re-add a question that is already in the bank.
        mockMvc.perform(post("/api/admin/lessons/" + ids.lesson + "/questions/generate").with(csrf()).with(as(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(delete("/api/admin/questions/" + questionId).with(csrf()).with(as(admin)))
                .andExpect(status().isNoContent());
        assertThat(questionRepository.count()).isEqualTo(2);
    }

    @Test
    void adminsCanTypeInQuestionsAndTheyAreApprovedInTheirOwnOrder() throws Exception {
        Ids ids = createLesson(false);

        mockMvc.perform(post("/api/admin/lessons/" + ids.lesson + "/questions").contentType(MediaType.APPLICATION_JSON)
                        .content(request("What is HMAC?", "EASY", "[\"A hash\",\"A cipher\",\"A cookie\",\"A port\"]", 0))
                        .with(csrf()).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.options[0].text").value("A hash"))
                .andExpect(jsonPath("$.options[0].correct").value(true));

        mockMvc.perform(post("/api/admin/lessons/" + ids.lesson + "/questions").contentType(MediaType.APPLICATION_JSON)
                        .content(request("Same options?", "EASY", "[\"A\",\"a\",\"C\",\"D\"]", 0)).with(csrf()).with(as(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("different")));
        mockMvc.perform(post("/api/admin/lessons/" + ids.lesson + "/questions").contentType(MediaType.APPLICATION_JSON)
                        .content(request("Too few?", "EASY", "[\"A\",\"B\"]", 0)).with(csrf()).with(as(admin)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void importsAValidCsvAndReportsTheBadRows() throws Exception {
        Ids ids = createLesson(false);
        String csv = "question,difficulty,option1,option2,option3,option4,correct,explanation\n"
                + "\"What is 2+2, really?\",easy,3,4,5,6,2,Basic sums\n"
                + "Bad difficulty,impossible,a,b,c,d,1,\n"
                + "Bad correct,hard,a,b,c,d,9,\n"
                + "Second good one,MEDIUM,w,x,y,z,C,\n";

        mockMvc.perform(multipart("/api/admin/lessons/" + ids.lesson + "/questions/import")
                        .file(new MockMultipartFile("file", "questions.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                        .with(csrf()).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(2))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].line").value(3))
                .andExpect(jsonPath("$.errors[0].message").value(containsString("EASY, MEDIUM or HARD")))
                .andExpect(jsonPath("$.errors[1].line").value(4));

        mockMvc.perform(get("/api/admin/courses/" + ids.course + "/questions").with(as(admin)))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].source").value("IMPORT"))
                .andExpect(jsonPath("$[0].status").value("APPROVED"))
                .andExpect(jsonPath("$[0].options[1].correct").value(true))
                .andExpect(jsonPath("$[1].options[2].correct").value(true));

        mockMvc.perform(multipart("/api/admin/lessons/" + ids.lesson + "/questions/import")
                        .file(new MockMultipartFile("file", "q.csv", "text/csv", "question,difficulty\nx,easy\n".getBytes(StandardCharsets.UTF_8)))
                        .with(csrf()).with(as(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("missing these columns")));
        mockMvc.perform(multipart("/api/admin/lessons/" + ids.lesson + "/questions/import")
                        .file(new MockMultipartFile("file", "q.txt", "text/plain", "x".getBytes(StandardCharsets.UTF_8)))
                        .with(csrf()).with(as(admin)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void viewersCannotTouchTheQuestionBank() throws Exception {
        Ids ids = createLesson(true);

        mockMvc.perform(get("/api/admin/courses/" + ids.course + "/questions").with(as(viewer))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/lessons/" + ids.lesson + "/questions/generate").with(csrf()).with(as(viewer)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/lessons/" + ids.lesson + "/questions").contentType(MediaType.APPLICATION_JSON)
                        .content(request("Q?", "EASY", FOUR, 0)).with(csrf()).with(as(viewer)))
                .andExpect(status().isForbidden());
    }

    @Test
    void generationNeedsATranscriptAndReportsAiProblemsClearly() throws Exception {
        Ids withoutTranscript = createLesson(false);
        mockMvc.perform(post("/api/admin/lessons/" + withoutTranscript.lesson + "/questions/generate").with(csrf()).with(as(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("transcript")));

        Ids ids = createLesson(true);
        when(llmClient.complete(anyString(), anyString())).thenThrow(new AiException("The AI service answered HTTP 429"));
        mockMvc.perform(post("/api/admin/lessons/" + ids.lesson + "/questions/generate").with(csrf()).with(as(admin)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value(containsString("429")));

        doThrow(new AiNotConfiguredException()).when(llmClient).complete(anyString(), anyString());
        mockMvc.perform(post("/api/admin/lessons/" + ids.lesson + "/questions/generate").with(csrf()).with(as(admin)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value(containsString("AI_MODEL")));
    }

    @Test
    void questionsGoWithTheirLessonAndCourse() throws Exception {
        Ids ids = createLesson(false);
        mockMvc.perform(post("/api/admin/lessons/" + ids.lesson + "/questions").contentType(MediaType.APPLICATION_JSON)
                        .content(request("Q?", "EASY", FOUR, 0)).with(csrf()).with(as(admin)))
                .andExpect(status().isOk());
        assertThat(questionRepository.count()).isEqualTo(1);

        mockMvc.perform(delete("/api/admin/lessons/" + ids.lesson).with(csrf()).with(as(admin))).andExpect(status().isNoContent());
        assertThat(questionRepository.count()).as("deleting a lesson deletes its questions").isZero();
    }

    // ---- helpers ----

    private record Ids(String course, String module, String lesson) {
    }

    private Ids createLesson(boolean withTranscript) throws Exception {
        MockMultipartHttpServletRequestBuilder course = multipart("/api/admin/courses");
        course.param("title", "Question bank course");
        String courseId = json(mockMvc.perform(course.with(csrf()).with(as(admin))).andExpect(status().isOk()).andReturn()).get("id").asText();

        String moduleId = json(mockMvc.perform(post("/api/admin/courses/" + courseId + "/modules")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Intro\"}").with(csrf()).with(as(admin)))
                .andExpect(status().isOk()).andReturn()).get("id").asText();

        byte[] head = {0x00, 0x00, 0x00, 0x1C, 'f', 't', 'y', 'p', 'm', 'p', '4', '2',
                0x00, 0x00, 0x00, 0x00, 'm', 'p', '4', '2', 'i', 's', 'o', 'm', 'a', 'v', 'c', '1'};
        MockMultipartHttpServletRequestBuilder lesson = multipart("/api/admin/modules/" + moduleId + "/lessons");
        lesson.file(new MockMultipartFile("video", "lecture.mp4", "video/mp4", head));
        if (withTranscript) {
            lesson.file(new MockMultipartFile("transcript", "zoom.vtt", "text/vtt",
                    ("WEBVTT\n\n1\n00:00:01.000 --> 00:00:09.000\nTickets are signed with HMAC.\n\n"
                            + "2\n00:01:05.000 --> 00:01:12.000\nThey are bound to the session.\n").getBytes(StandardCharsets.UTF_8)));
        }
        lesson.param("title", "Signed tickets");
        String lessonId = json(mockMvc.perform(lesson.with(csrf()).with(as(admin))).andExpect(status().isOk()).andReturn()).get("id").asText();
        return new Ids(courseId, moduleId, lessonId);
    }

    private static String q(String text, String difficulty, int correct, int seconds) {
        return "{\"question\":\"" + text + "\",\"difficulty\":\"" + difficulty + "\",\"options\":" + FOUR
                + ",\"correctIndex\":" + correct + ",\"timestampSeconds\":" + seconds + ",\"explanation\":\"Because.\"}";
    }

    private static String request(String text, String difficulty, String options, int correct) {
        return "{\"text\":\"" + text + "\",\"difficulty\":\"" + difficulty + "\",\"explanation\":\"Because.\","
                + "\"options\":" + options + ",\"correctIndex\":" + correct + "}";
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private User user(String email, Role role) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> userRepository.save(new User(email, "Bank Flow Test", null, role)));
    }
}
