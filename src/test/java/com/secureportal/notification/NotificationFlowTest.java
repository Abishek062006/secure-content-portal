package com.secureportal.notification;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import static com.secureportal.testsupport.TestPrincipals.as;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Notifications are created by business events elsewhere (course publish, new lesson, admin broadcast) —
 *  this drives those real endpoints rather than calling NotificationService directly, so it also proves
 *  the triggers are actually wired in. */
@SpringBootTest(properties = {"storage.provider=local", "storage.local-path=target/notification-test-storage"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*local.*")
class NotificationFlowTest {

    private static final String ADMIN_EMAIL = "notif-flow-admin@example.com";
    private static final String VIEWER_EMAIL = "notif-flow-viewer@example.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private NotificationRepository notificationRepository;
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
        notificationRepository.deleteAll();
        courseRepository.deleteAll();
        userRepository.findByEmailIgnoreCase(ADMIN_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmailIgnoreCase(VIEWER_EMAIL).ifPresent(userRepository::delete);
    }

    @Test
    void publishingACourseNotifiesLearnersAndTheFeedCanBeReadMarkedAndCleared() throws Exception {
        String courseId = createAndPublish("Kubernetes for platform teams");

        // Publishing notified every learner — including one who hasn't enrolled yet.
        mockMvc.perform(get("/api/notifications/unread-count").with(as(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));
        MvcResult recent = mockMvc.perform(get("/api/notifications/recent").with(as(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value("COURSE"))
                .andExpect(jsonPath("$[0].title").value("New course available"))
                .andExpect(jsonPath("$[0].isRead").value(false))
                .andReturn();
        long firstNotificationId = json(recent).get(0).get("id").asLong();

        // Enroll, then a new lesson notifies only the (now) enrolled learner.
        mockMvc.perform(post("/api/courses/" + courseId + "/enroll").with(csrf()).with(as(viewer)))
                .andExpect(status().isOk());
        String module = json(mockMvc.perform(post("/api/admin/courses/" + courseId + "/modules")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"M2\"}")
                        .with(csrf()).with(as(admin))).andReturn()).get("id").asText();
        mockMvc.perform(multipart("/api/admin/modules/" + module + "/lessons")
                        .file(new org.springframework.mock.web.MockMultipartFile("video", "l2.mp4", "video/mp4", mp4()))
                        .param("title", "L2").with(csrf()).with(as(admin)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notifications/unread-count").with(as(viewer)))
                .andExpect(jsonPath("$.unreadCount").value(2));

        // Mark the first as read, then the unread count drops and the unread-only filter excludes it.
        mockMvc.perform(put("/api/notifications/" + firstNotificationId + "/read").with(csrf()).with(as(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRead").value(true));
        mockMvc.perform(get("/api/notifications/unread-count").with(as(viewer)))
                .andExpect(jsonPath("$.unreadCount").value(1));
        mockMvc.perform(get("/api/notifications?unreadOnly=true").with(as(viewer)))
                .andExpect(jsonPath("$.content.length()").value(1));

        // A learner can't read another learner's notification.
        mockMvc.perform(delete("/api/notifications/" + firstNotificationId).with(csrf()).with(as(admin)))
                .andExpect(status().isNotFound());

        // Admin-only categories are invisible to a learner even if requested explicitly.
        mockMvc.perform(get("/api/notifications?category=CONTENT").with(as(viewer)))
                .andExpect(jsonPath("$.content.length()").value(0));

        // Clearing read notifications removes the one already marked read but keeps the unread one.
        mockMvc.perform(delete("/api/notifications/clear-read").with(csrf()).with(as(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cleared").value(1));
        mockMvc.perform(get("/api/notifications/unread-count").with(as(viewer)))
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    void onlyAdminsCanBroadcastAndAudienceTargetingIsRespected() throws Exception {
        mockMvc.perform(post("/api/admin/notifications/announcement").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Maintenance\",\"message\":\"Downtime tonight\",\"targetAudience\":\"LEARNERS\"}")
                        .with(csrf()).with(as(viewer)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/notifications/announcement").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Maintenance\",\"message\":\"Downtime tonight\",\"targetAudience\":\"LEARNERS\"}")
                        .with(csrf()).with(as(admin)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notifications/unread-count").with(as(viewer)))
                .andExpect(jsonPath("$.unreadCount").value(1));
        mockMvc.perform(get("/api/notifications/unread-count").with(as(admin)))
                .andExpect(jsonPath("$.unreadCount").value(0));
    }

    // ---- helpers ----

    private String createAndPublish(String title) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/admin/courses");
        request.param("title", title);
        String courseId = json(mockMvc.perform(request.with(csrf()).with(as(admin)))
                .andExpect(status().isOk()).andReturn()).get("id").asText();

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
                .orElseGet(() -> userRepository.save(new User(email, "Notification Flow Test", null, role)));
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
