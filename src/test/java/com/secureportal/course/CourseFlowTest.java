package com.secureportal.course;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureportal.user.Role;
import com.secureportal.user.User;
import com.secureportal.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole course flow through the real filter chain, controllers and
 * database, with files on local disk. Writes rows, so it only runs when
 * the local profile is active — see .env.local.
 */
@SpringBootTest(properties = {"storage.provider=local", "storage.local-path=target/course-test-storage"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*local.*")
class CourseFlowTest {

    private static final String ADMIN_EMAIL = "course-flow-admin@example.com";
    private static final String VIEWER_EMAIL = "course-flow-viewer@example.com";
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");
    private static final byte[] VIDEO = mp4(4096);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private CourseModuleRepository moduleRepository;
    @Autowired
    private LessonRepository lessonRepository;
    @Autowired
    private EnrollmentRepository enrollmentRepository;
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
        courseRepository.deleteAll();
        userRepository.findByEmailIgnoreCase(ADMIN_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmailIgnoreCase(VIEWER_EMAIL).ifPresent(userRepository::delete);
    }

    @Test
    void adminBuildsACourseAndALearnerEnrollsWatchesAndProgresses() throws Exception {
        String courseId = createCourse("Signed tickets 101");
        mockMvc.perform(get("/api/courses").with(as(viewer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/courses/" + courseId).with(as(viewer))).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/admin/courses/" + courseId + "/publish").with(csrf()).with(as(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("at least one lesson")));

        String intro = addModule(courseId, "Intro");
        String advanced = addModule(courseId, "Advanced");
        String hmac = addLesson(intro, "What is HMAC?", true);
        String replay = addLesson(intro, "Replay protection", false);
        String sessions = addLesson(advanced, "Session binding", false);

        mockMvc.perform(put("/api/admin/modules/" + intro + "/lessons/order").contentType(MediaType.APPLICATION_JSON)
                        .content(ids(replay, hmac)).with(csrf()).with(as(admin)))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/admin/courses/" + courseId + "/modules/order").contentType(MediaType.APPLICATION_JSON)
                        .content(ids(advanced, intro)).with(csrf()).with(as(admin)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/courses/" + courseId).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules[0].title").value("Advanced"))
                .andExpect(jsonPath("$.modules[1].title").value("Intro"))
                .andExpect(jsonPath("$.modules[1].lessons[0].title").value("Replay protection"))
                .andExpect(jsonPath("$.modules[1].lessons[1].title").value("What is HMAC?"))
                .andExpect(jsonPath("$.modules[1].lessons[1].hasTranscript").value(true))
                .andExpect(jsonPath("$.course.moduleCount").value(2))
                .andExpect(jsonPath("$.course.lessonCount").value(3));

        mockMvc.perform(post("/api/admin/courses/" + courseId + "/publish").with(csrf()).with(as(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));

        MvcResult listed = mockMvc.perform(get("/api/courses").with(as(viewer))).andExpect(status().isOk()).andReturn();
        JsonNode card = json(listed).get(0);
        assertThat(card.get("lessonCount").asInt()).isEqualTo(3);
        assertThat(card.get("enrolled").asBoolean()).isFalse();
        assertThat(card.get("viewCount").isNull()).as("view stats are admin-only").isTrue();
        mockMvc.perform(get("/api/courses/" + courseId + "/thumbnail").with(as(viewer)))
                .andExpect(status().isOk()).andExpect(content().bytes(PNG));

        mockMvc.perform(get("/api/courses/" + courseId + "/lessons/" + hmac).with(as(viewer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(containsString("Enroll")));
        mockMvc.perform(post("/api/courses/" + courseId + "/enroll").with(csrf()).with(as(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrolled").value(true))
                .andExpect(jsonPath("$.progressPercent").value(0))
                .andExpect(jsonPath("$.resumeLessonId").value(sessions));

        MvcResult opened = mockMvc.perform(get("/api/courses/" + courseId + "/lessons/" + hmac).with(as(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transcript.length()").value(2))
                .andExpect(jsonPath("$.previousLessonId").value(replay))
                .andExpect(jsonPath("$.nextLessonId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.moduleTitle").value("Intro"))
                .andReturn();
        String ticket = json(opened).get("ticket").asText();
        Cookie session = opened.getResponse().getCookie("SESSION");
        assertThat(session).as("the ticket must be bound to a real session").isNotNull();

        mockMvc.perform(get("/api/course-stream/" + ticket).cookie(session).with(as(viewer)))
                .andExpect(status().isOk())
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(content().bytes(VIDEO));
        mockMvc.perform(get("/api/course-stream/" + ticket).cookie(session).with(as(viewer)).header("Range", "bytes=10-19"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 10-19/" + VIDEO.length))
                .andExpect(content().bytes(Arrays.copyOfRange(VIDEO, 10, 20)));
        mockMvc.perform(get("/api/course-stream/" + ticket).with(as(viewer))).andExpect(status().isForbidden());

        mockMvc.perform(put("/api/courses/" + courseId + "/lessons/" + hmac + "/progress")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"positionSeconds\":42,\"completed\":false}")
                        .with(csrf()).with(as(viewer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completed").value(false));
        mockMvc.perform(get("/api/courses/" + courseId + "/lessons/" + hmac).with(as(viewer)))
                .andExpect(jsonPath("$.resumeSeconds").value(42));

        mockMvc.perform(put("/api/courses/" + courseId + "/lessons/" + hmac + "/progress")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"positionSeconds\":90,\"completed\":true}")
                        .with(csrf()).with(as(viewer)))
                .andExpect(jsonPath("$.completed").value(true)).andExpect(jsonPath("$.progressPercent").value(33));
        mockMvc.perform(put("/api/courses/" + courseId + "/lessons/" + hmac + "/progress")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"positionSeconds\":3,\"completed\":false}")
                        .with(csrf()).with(as(viewer)))
                .andExpect(jsonPath("$.completed").value(true));

        mockMvc.perform(get("/api/courses/" + courseId).with(as(viewer)))
                .andExpect(jsonPath("$.progressPercent").value(33))
                .andExpect(jsonPath("$.resumeLessonId").value(hmac))
                .andExpect(jsonPath("$.modules[1].lessons[1].completed").value(true))
                .andExpect(jsonPath("$.modules[1].lessons[0].completed").value(false));

        mockMvc.perform(post("/api/admin/modules/" + intro + "/lessons").contentType(MediaType.MULTIPART_FORM_DATA)
                        .with(csrf()).with(as(viewer)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/courses/" + courseId + "/modules").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"sneaky\"}").with(csrf()).with(as(viewer)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/admin/lessons/" + replay).with(csrf()).with(as(admin))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/admin/courses/" + courseId).with(as(admin)))
                .andExpect(jsonPath("$.modules[1].lessons.length()").value(1))
                .andExpect(jsonPath("$.modules[1].lessons[0].position").value(0));
        mockMvc.perform(delete("/api/admin/modules/" + advanced).with(csrf()).with(as(admin))).andExpect(status().isNoContent());
        assertThat(lessonRepository.count()).isEqualTo(1);
        mockMvc.perform(get("/api/admin/courses/" + courseId).with(as(admin)))
                .andExpect(jsonPath("$.modules.length()").value(1))
                .andExpect(jsonPath("$.modules[0].position").value(0));

        mockMvc.perform(delete("/api/admin/courses/" + courseId).with(csrf()).with(as(admin))).andExpect(status().isNoContent());
        assertThat(moduleRepository.count()).isZero();
        assertThat(lessonRepository.count()).isZero();
        assertThat(enrollmentRepository.count()).isZero();
        mockMvc.perform(get("/api/courses/" + courseId).with(as(viewer))).andExpect(status().isNotFound());
    }

    @Test
    void aRejectedLessonUploadLeavesNothingBehind() throws Exception {
        String module = addModule(createCourse("Bad uploads"), "Intro");

        mockMvc.perform(lessonRequest(module, "Bad transcript", VIDEO, "not a transcript".getBytes(StandardCharsets.UTF_8))
                        .with(csrf()).with(as(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("WEBVTT")));
        mockMvc.perform(lessonRequest(module, "Fake video", "just text".getBytes(StandardCharsets.UTF_8), null)
                        .with(csrf()).with(as(admin)))
                .andExpect(status().isBadRequest());

        assertThat(lessonRepository.count()).isZero();
    }

    @Test
    void aReorderMustListEveryItemExactlyOnce() throws Exception {
        String courseId = createCourse("Ordering");
        String first = addModule(courseId, "One");
        addModule(courseId, "Two");

        mockMvc.perform(put("/api/admin/courses/" + courseId + "/modules/order").contentType(MediaType.APPLICATION_JSON)
                        .content(ids(first)).with(csrf()).with(as(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("exactly once")));
    }

    @Test
    void adminsCanPreviewADraftButLearnersCannotSeeIt() throws Exception {
        String courseId = createCourse("Draft course");

        mockMvc.perform(get("/api/courses/" + courseId).with(as(admin))).andExpect(status().isOk());
        mockMvc.perform(get("/api/courses/" + courseId).with(as(viewer))).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/courses/" + courseId + "/enroll").with(csrf()).with(as(viewer))).andExpect(status().isNotFound());
    }

    // ---- helpers ----

    private String createCourse(String title) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/admin/courses");
        request.file(new MockMultipartFile("thumbnail", "cover.png", "image/png", PNG));
        request.param("title", title);
        request.param("category", "Security");
        MvcResult created = mockMvc.perform(request.with(csrf()).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();
        return json(created).get("id").asText();
    }

    private String addModule(String courseId, String title) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/admin/courses/" + courseId + "/modules")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"" + title + "\"}")
                        .with(csrf()).with(as(admin)))
                .andExpect(status().isOk()).andReturn();
        return json(created).get("id").asText();
    }

    private String addLesson(String moduleId, String title, boolean withTranscript) throws Exception {
        byte[] transcript = withTranscript
                ? ("WEBVTT\n\n1\n00:00:01.000 --> 00:00:04.500\nWelcome.\n\n2\n00:01:05.250 --> 00:01:09.000\nSigned tickets.\n")
                        .getBytes(StandardCharsets.UTF_8)
                : null;
        MvcResult created = mockMvc.perform(lessonRequest(moduleId, title, VIDEO, transcript).with(csrf()).with(as(admin)))
                .andExpect(status().isOk()).andReturn();
        return json(created).get("id").asText();
    }

    private MockMultipartHttpServletRequestBuilder lessonRequest(String moduleId, String title, byte[] video, byte[] transcript) {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/admin/modules/" + moduleId + "/lessons");
        request.file(new MockMultipartFile("video", "lecture.mp4", "video/mp4", video));
        if (transcript != null) {
            request.file(new MockMultipartFile("transcript", "zoom.vtt", "text/vtt", transcript));
        }
        request.param("title", title);
        return request;
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String ids(String... ids) {
        return "{\"ids\":" + List.of(ids).stream().map(id -> "\"" + id + "\"").toList() + "}";
    }

    private User user(String email, Role role) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> userRepository.save(new User(email, "Course Flow Test", null, role)));
    }

    private static byte[] mp4(int size) {
        byte[] head = {0x00, 0x00, 0x00, 0x1C, 'f', 't', 'y', 'p', 'm', 'p', '4', '2',
                0x00, 0x00, 0x00, 0x00, 'm', 'p', '4', '2', 'i', 's', 'o', 'm', 'a', 'v', 'c', '1'};
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) {
            bytes[i] = (byte) (i % 251);
        }
        System.arraycopy(head, 0, bytes, 0, head.length);
        return bytes;
    }

    @Test
    void coursePricingIsValidatedShownToLearnersAndDiscountsApplyOnlyWhilePeriodRuns() throws Exception {
        java.time.Instant end = java.time.Instant.now().plus(5, java.time.temporal.ChronoUnit.DAYS);
        String id = json(mockMvc.perform(multipart("/api/admin/courses").param("title", "Priced course")
                        .param("priceRupees", "1249").param("discountPercent", "20").param("discountEnd", end.toString())
                        .with(csrf()).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pricing.priceRupees").value(1249))
                .andExpect(jsonPath("$.pricing.discountActive").value(true))
                .andExpect(jsonPath("$.pricing.finalPriceRupees").value(999))
                .andExpect(jsonPath("$.pricing.free").value(false))
                .andReturn()).get("id").asText();

        String module = json(mockMvc.perform(post("/api/admin/courses/" + id + "/modules").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"M\"}").with(csrf()).with(as(admin))).andReturn()).get("id").asText();
        mockMvc.perform(lessonRequest(module, "L", VIDEO, null).with(csrf()).with(as(admin))).andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/courses/" + id + "/publish").with(csrf()).with(as(admin))).andExpect(status().isOk());

        mockMvc.perform(get("/api/courses").with(as(viewer)))
                .andExpect(jsonPath("$[0].pricing.finalPriceRupees").value(999))
                .andExpect(jsonPath("$[0].pricing.discountPercent").value(20))
                .andExpect(jsonPath("$[0].pricing.discountEnd").exists());

        // Removing the discount, going free, and rejecting nonsense.
        mockMvc.perform(put("/api/admin/courses/" + id + "/pricing").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priceRupees\":1249,\"discountPercent\":0}").with(csrf()).with(as(admin)))
                .andExpect(jsonPath("$.pricing.finalPriceRupees").value(1249)).andExpect(jsonPath("$.pricing.discountActive").value(false));
        mockMvc.perform(put("/api/admin/courses/" + id + "/pricing").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priceRupees\":0}").with(csrf()).with(as(admin)))
                .andExpect(jsonPath("$.pricing.free").value(true));
        mockMvc.perform(put("/api/admin/courses/" + id + "/pricing").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priceRupees\":500,\"discountPercent\":30}").with(csrf()).with(as(admin)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/admin/courses/" + id + "/pricing").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priceRupees\":500}").with(csrf()).with(as(viewer)))
                .andExpect(status().isForbidden());
    }
}
