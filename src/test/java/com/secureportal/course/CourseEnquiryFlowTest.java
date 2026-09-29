package com.secureportal.course;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static com.secureportal.testsupport.TestPrincipals.as;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Enquire" is on every course regardless of access type, doesn't need enrollment, and just puts the
 *  details in front of an admin — nothing here contacts anyone automatically. */
@SpringBootTest(properties = {"storage.provider=local", "storage.local-path=target/course-enquiry-test-storage"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*local.*")
class CourseEnquiryFlowTest {

    private static final String ADMIN_EMAIL = "enquiry-flow-admin@example.com";
    private static final String VIEWER_EMAIL = "enquiry-flow-viewer@example.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CourseRepository courseRepository;
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
    void aLearnerCanEnquireWithoutEnrollingAndAnAdminCanMarkItContacted() throws Exception {
        String courseId = createAndPublish("Kubernetes for platform teams");

        // No enrollment needed — the whole point is to reach someone who hasn't committed yet.
        String body = "{\"name\":\"Priya\",\"email\":\"priya@example.com\",\"phone\":\"9876543210\","
                + "\"message\":\"Does this cover multi-cluster setups?\"}";
        MvcResult submitted = mockMvc.perform(post("/api/courses/" + courseId + "/enquiry")
                        .contentType(MediaType.APPLICATION_JSON).content(body).with(csrf()).with(as(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.courseTitle").value("Kubernetes for platform teams"))
                .andReturn();
        long enquiryId = json(submitted).get("id").asLong();

        // Missing/invalid fields are rejected.
        mockMvc.perform(post("/api/courses/" + courseId + "/enquiry").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"priya@example.com\"}").with(csrf()).with(as(viewer)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/courses/" + courseId + "/enquiry").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Priya\",\"email\":\"not-an-email\"}").with(csrf()).with(as(viewer)))
                .andExpect(status().isBadRequest());

        // A regular viewer can't see the inbox.
        mockMvc.perform(get("/api/admin/enquiries").with(as(viewer))).andExpect(status().isForbidden());

        // The admin sees it in the NEW queue.
        mockMvc.perform(get("/api/admin/enquiries?status=NEW").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Priya"))
                .andExpect(jsonPath("$[0].phone").value("9876543210"))
                .andExpect(jsonPath("$[0].message").value(org.hamcrest.Matchers.containsString("multi-cluster")));

        // Marking it contacted moves it out of the NEW queue but keeps it in the full list.
        mockMvc.perform(post("/api/admin/enquiries/" + enquiryId + "/contacted").with(csrf()).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONTACTED"))
                .andExpect(jsonPath("$.contactedBy").value(ADMIN_EMAIL));
        mockMvc.perform(get("/api/admin/enquiries?status=NEW").with(as(admin)))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/admin/enquiries").with(as(admin)))
                .andExpect(jsonPath("$[0].status").value("CONTACTED"));
    }

    // ---- helpers ----

    private String createAndPublish(String title) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/admin/courses");
        request.param("title", title);
        MvcResult created = mockMvc.perform(request.with(csrf()).with(as(admin)))
                .andExpect(status().isOk()).andReturn();
        String courseId = json(created).get("id").asText();

        String module = json(mockMvc.perform(post("/api/admin/courses/" + courseId + "/modules")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"M\"}")
                        .with(csrf()).with(as(admin))).andReturn()).get("id").asText();
        mockMvc.perform(multipart("/api/admin/modules/" + module + "/lessons")
                        .file(new org.springframework.mock.web.MockMultipartFile("video", "l.mp4", "video/mp4", mp4()))
                        .param("title", "L").with(csrf()).with(as(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/courses/" + courseId + "/publish").with(csrf()).with(as(admin)))
                .andExpect(status().isOk());
        return courseId;
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private User user(String email, Role role) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> userRepository.save(new User(email, "Enquiry Flow Test", null, role)));
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
