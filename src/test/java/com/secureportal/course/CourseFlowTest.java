package com.secureportal.course;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureportal.auth.AppPrincipal;
import com.secureportal.user.Role;
import com.secureportal.user.User;
import com.secureportal.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole course flow through the real filter chain, controllers and
 * database, with files on local disk. Writes rows, so it only runs when
 * the local profile (isolated schema) is active — see .env.local —
 * never against the live tables.
 */
@SpringBootTest(properties = {"storage.provider=local", "storage.local-path=target/course-test-storage"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*local.*")
class CourseFlowTest {

    private static final String ADMIN_EMAIL = "course-flow-admin@example.com";
    private static final String VIEWER_EMAIL = "course-flow-viewer@example.com";
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void cleanUp() {
        courseRepository.deleteAll();
        userRepository.findByEmailIgnoreCase(ADMIN_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmailIgnoreCase(VIEWER_EMAIL).ifPresent(userRepository::delete);
    }

    @Test
    void adminUploadsACourseAndAViewerStreamsItThroughASessionBoundTicket() throws Exception {
        User admin = user(ADMIN_EMAIL, Role.ADMIN);
        User viewer = user(VIEWER_EMAIL, Role.VIEWER);
        byte[] videoBytes = mp4(4096);

        MvcResult created = mockMvc.perform(multipart("/api/admin/courses")
                        .file(new MockMultipartFile("video", "lecture.mp4", "video/mp4", videoBytes))
                        .file(new MockMultipartFile("thumbnail", "cover.png", "image/png", PNG))
                        .file(new MockMultipartFile("transcript", "zoom.vtt", "text/vtt", vtt()))
                        .param("title", "Signed tickets 101")
                        .param("description", "How the portal protects video")
                        .param("category", "Security")
                        .with(csrf()).with(as(admin)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode course = objectMapper.readTree(created.getResponse().getContentAsString());
        String id = course.get("id").asText();
        assertThat(course.get("thumbnailUrl").asText()).startsWith("/api/courses/" + id + "/thumbnail");
        assertThat(course.get("hasTranscript").asBoolean()).isTrue();
        assertThat(course.get("viewCount").asLong()).isZero();

        MvcResult list = mockMvc.perform(get("/api/courses").with(as(viewer)))
                .andExpect(status().isOk()).andReturn();
        JsonNode first = objectMapper.readTree(list.getResponse().getContentAsString()).get(0);
        assertThat(first.get("title").asText()).isEqualTo("Signed tickets 101");
        assertThat(first.get("viewCount").isNull()).as("view stats are admin-only").isTrue();

        mockMvc.perform(get("/api/courses/" + id + "/thumbnail").with(as(viewer)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(content().bytes(PNG));

        MvcResult detail = mockMvc.perform(get("/api/courses/" + id).with(as(viewer)))
                .andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(detail.getResponse().getContentAsString());
        String ticket = body.get("ticket").asText();
        assertThat(body.get("transcript")).hasSize(2);
        assertThat(body.get("transcript").get(1).get("start").asDouble()).isEqualTo(65.25);
        Cookie session = detail.getResponse().getCookie("SESSION");
        assertThat(session).as("the ticket must be bound to a real session").isNotNull();

        mockMvc.perform(get("/api/course-stream/" + ticket).cookie(session).with(as(viewer)))
                .andExpect(status().isOk())
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(content().bytes(videoBytes));

        mockMvc.perform(get("/api/course-stream/" + ticket).cookie(session).with(as(viewer))
                        .header("Range", "bytes=10-19"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 10-19/" + videoBytes.length))
                .andExpect(content().bytes(Arrays.copyOfRange(videoBytes, 10, 20)));

        mockMvc.perform(get("/api/course-stream/" + ticket).with(as(viewer)))
                .andExpect(status().isForbidden());

        mockMvc.perform(multipart("/api/admin/courses")
                        .file(new MockMultipartFile("video", "lecture.mp4", "video/mp4", videoBytes))
                        .param("title", "sneaky").with(csrf()).with(as(viewer)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/admin/courses/" + id).with(csrf()).with(as(admin)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/courses/" + id + "/thumbnail").with(as(viewer)))
                .andExpect(status().isNotFound());
    }

    @Test
    void aRejectedThumbnailLeavesNoCourseBehind() throws Exception {
        User admin = user(ADMIN_EMAIL, Role.ADMIN);

        mockMvc.perform(multipart("/api/admin/courses")
                        .file(new MockMultipartFile("video", "lecture.mp4", "video/mp4", mp4(1024)))
                        .file(new MockMultipartFile("thumbnail", "cover.png", "image/png",
                                "not an image".getBytes(StandardCharsets.UTF_8)))
                        .param("title", "Bad cover").with(csrf()).with(as(admin)))
                .andExpect(status().isBadRequest());

        assertThat(courseRepository.count()).isZero();
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

    private static byte[] vtt() {
        return ("WEBVTT\n\n1\n00:00:01.000 --> 00:00:04.500\nWelcome.\n\n"
                + "2\n00:01:05.250 --> 00:01:09.000\nSigned tickets.\n").getBytes(StandardCharsets.UTF_8);
    }

    private RequestPostProcessor as(User user) {
        OidcIdToken idToken = new OidcIdToken("test-id-token", Instant.now(), Instant.now().plusSeconds(3600),
                Map.of("sub", "test-subject", "iss", "https://accounts.google.com", "email", user.getEmail()));
        OidcUserInfo userInfo = new OidcUserInfo(Map.of("sub", "test-subject", "email", user.getEmail()));
        DefaultOidcUser delegate = new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken, userInfo);
        AppPrincipal principal = new AppPrincipal(user, delegate);
        return authentication(new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google"));
    }
}
