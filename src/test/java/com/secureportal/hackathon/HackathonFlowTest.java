package com.secureportal.hackathon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureportal.audit.AuditRepository;
import com.secureportal.gamification.GamificationService;
import com.secureportal.storage.StorageObject;
import com.secureportal.storage.StorageService;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

import java.util.List;

import static com.secureportal.testsupport.TestPrincipals.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Hackathons are admin-managed and strictly validated (an admin can't hand learners a javascript: link), and registering pays
 * its points once per learner.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*local.*")
class HackathonFlowTest {

    private static final String ADMIN = "hackathon-admin@example.com";
    private static final String LEARNER = "hackathon-learner@example.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private HackathonRepository hackathonRepository;
    @Autowired
    private GamificationService gamification;
    @Autowired
    private AuditRepository auditRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private StorageService storage;

    private User admin;
    private User learner;

    @BeforeEach
    void users() {
        admin = user(ADMIN, Role.ADMIN);
        learner = user(LEARNER, Role.VIEWER);
    }

    @AfterEach
    void cleanUp() {
        hackathonRepository.deleteAll();
        for (String email : List.of(ADMIN, LEARNER)) {
            userRepository.findByEmailIgnoreCase(email).ifPresent(userRepository::delete);
        }
    }

    private User user(String email, Role role) {
        return userRepository.findByEmailIgnoreCase(email).orElseGet(() -> userRepository.save(new User(email, email, null, role)));
    }

    private static String hackathon(String... overrides) {
        java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();
        fields.put("title", "\"Campus Code Sprint\"");
        fields.put("organizer", "\"GradientNova\"");
        fields.put("stream", "\"Engineering & Web Dev\"");
        fields.put("mode", "\"ONLINE\"");
        fields.put("status", "\"ACTIVE\"");
        fields.put("registrationUrl", "\"https://example.com/sprint\"");
        for (int i = 0; i < overrides.length; i += 2) {
            fields.put(overrides[i], overrides[i + 1]);
        }
        StringBuilder json = new StringBuilder("{");
        fields.forEach((k, v) -> json.append(json.length() > 1 ? "," : "").append('"').append(k).append("\":").append(v));
        return json.append('}').toString();
    }

    private long create(String body) throws Exception {
        String response = mockMvc.perform(post("/api/admin/hackathons").contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(csrf()).with(as(admin)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private void createRejected(String body, String messagePart) throws Exception {
        mockMvc.perform(post("/api/admin/hackathons").contentType(MediaType.APPLICATION_JSON).content(body).with(csrf()).with(as(admin)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value(containsString(messagePart)));
    }

    @Test
    void onlyAdminsManageHackathonsAndOnlyWithValidContent() throws Exception {
        mockMvc.perform(post("/api/admin/hackathons").contentType(MediaType.APPLICATION_JSON).content(hackathon())
                .with(csrf()).with(as(learner))).andExpect(status().isForbidden());

        createRejected(hackathon("registrationUrl", "\"javascript:alert(1)\""), "https://");
        createRejected(hackathon("registrationUrl", "\"http://example.com/plain\""), "https://");
        createRejected(hackathon("registrationUrl", "\"https://user:pw@example.com/x\""), "https://");
        createRejected(hackathon("registrationUrl", "\"\""), "required");
        createRejected(hackathon("bannerUrl", "\"data:image/png;base64,AAAA\""), "https://");
        createRejected(hackathon("title", "\"  \""), "title is required");
        createRejected(hackathon("title", "\"" + "T".repeat(201) + "\""), "at most 200");
        createRejected(hackathon("mode", "\"SPACE\""), "mode");
        createRejected(hackathon("status", "\"DELETED\""), "status");
        createRejected(hackathon("eventStartDate", "\"2026-10-10T10:00:00Z\"", "eventEndDate", "\"2026-10-09T10:00:00Z\""), "before it starts");
        assertThat(hackathonRepository.count()).isZero();

        long id = create(hackathon("bannerUrl", "\"https://images.example.com/banner.png\""));
        mockMvc.perform(put("/api/admin/hackathons/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(hackathon("title", "\"Renamed Sprint\"")).with(csrf()).with(as(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Renamed Sprint"));
        mockMvc.perform(put("/api/admin/hackathons/999999").contentType(MediaType.APPLICATION_JSON).content(hackathon())
                .with(csrf()).with(as(admin))).andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/admin/hackathons/" + id).with(csrf()).with(as(learner))).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/hackathons/" + id).with(csrf()).with(as(admin))).andExpect(status().isNoContent());
        assertThat(hackathonRepository.count()).isZero();

        assertThat(auditRepository.findAll()).extracting("action").contains("HACKATHON_CREATE", "HACKATHON_UPDATE", "HACKATHON_DELETE");
    }

    @Test
    void learnersSaveAndUnsaveListingsAndAdminsCannot() throws Exception {
        long id = create(hackathon());

        mockMvc.perform(get("/api/hackathons").with(as(learner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].saved").value(false)).andExpect(jsonPath("$[0].registrationOpen").value(true))
                .andExpect(jsonPath("$[0].registrationUrl").value("https://example.com/sprint"));

        // Saving twice leaves one save.
        mockMvc.perform(put("/api/hackathons/" + id + "/save").with(csrf()).with(as(learner))).andExpect(status().isOk());
        mockMvc.perform(put("/api/hackathons/" + id + "/save").with(csrf()).with(as(learner))).andExpect(status().isOk());
        mockMvc.perform(get("/api/hackathons").with(as(learner))).andExpect(jsonPath("$[0].saved").value(true));
        mockMvc.perform(get("/api/hackathons?saved=true").with(as(learner))).andExpect(jsonPath("$.length()").value(1));

        // Saving pays nothing: it isn't a registration.
        assertThat(gamification.summary(learner.getId()).totalPoints()).isZero();

        mockMvc.perform(delete("/api/hackathons/" + id + "/save").with(csrf()).with(as(learner))).andExpect(status().isOk());
        mockMvc.perform(get("/api/hackathons?saved=true").with(as(learner))).andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(put("/api/hackathons/" + id + "/save").with(csrf()).with(as(admin))).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/hackathons/999999/save").with(csrf()).with(as(learner))).andExpect(status().isNotFound());
    }

    @Test
    void closedListingsAreMarkedAndStillListed() throws Exception {
        create(hackathon("status", "\"COMPLETED\""));
        create(hackathon("title", "\"Late\"", "registrationDeadline", "\"2020-01-01T00:00:00Z\""));
        mockMvc.perform(get("/api/hackathons").with(as(learner)))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].registrationOpen").value(false)).andExpect(jsonPath("$[1].registrationOpen").value(false));
    }

    @Test
    void aCalendarFileIsOfferedOnlyForDatedEventsAndIsEscaped() throws Exception {
        long dated = create(hackathon("title", "\"Sprint; with, punctuation\"", "eventStartDate", "\"2026-11-01T09:00:00Z\"",
                "eventEndDate", "\"2026-11-02T09:00:00Z\""));
        long undated = create(hackathon("title", "\"No date yet\""));

        String ics = mockMvc.perform(get("/api/hackathons/" + dated + "/calendar.ics").with(as(learner)))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", containsString("text/calendar")))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andReturn().getResponse().getContentAsString();
        assertThat(ics).startsWith("BEGIN:VCALENDAR").contains("DTSTART:20261101T090000Z", "DTEND:20261102T090000Z")
                .contains("SUMMARY:Sprint\\; with\\, punctuation").contains("URL:https://example.com/sprint").endsWith("END:VCALENDAR\r\n");

        mockMvc.perform(get("/api/hackathons/" + undated + "/calendar.ics").with(as(learner))).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/hackathons/999999/calendar.ics").with(as(learner))).andExpect(status().isNotFound());
    }

    @Test
    void deletingAHackathonRemovesItsSaves() throws Exception {
        long id = create(hackathon());
        mockMvc.perform(put("/api/hackathons/" + id + "/save").with(csrf()).with(as(learner))).andExpect(status().isOk());
        mockMvc.perform(delete("/api/admin/hackathons/" + id).with(csrf()).with(as(admin))).andExpect(status().isNoContent());
        create(hackathon());
        mockMvc.perform(get("/api/hackathons?saved=true").with(as(learner))).andExpect(jsonPath("$.length()").value(0));
    }

    private static byte[] png() throws Exception {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private org.springframework.test.web.servlet.ResultActions uploadBanner(User who, long id, String name, byte[] bytes) throws Exception {
        return mockMvc.perform(multipart("/api/admin/hackathons/" + id + "/banner")
                .file(new MockMultipartFile("file", name, "image/png", bytes)).with(csrf()).with(as(who)));
    }

    @Test
    void anAdminCanUploadABannerThatIsStoredPrivatelyServedAndCleanedUp() throws Exception {
        long id = create(hackathon());
        byte[] image = png();

        // Only real images, and only from admins.
        uploadBanner(admin, id, "poster.png", "this is not an image".getBytes()).andExpect(status().isBadRequest());
        uploadBanner(learner, id, "poster.png", image).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/hackathons/" + id + "/banner").with(as(learner))).andExpect(status().isNotFound());

        uploadBanner(admin, id, "poster.png", image).andExpect(status().isOk())
                .andExpect(jsonPath("$.bannerUrl").value("/api/hackathons/" + id + "/banner"));
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(storage).put(key.capture(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.eq("image/png"));
        assertThat(key.getValue()).startsWith("hackathons/" + id + "/banner/");

        org.mockito.Mockito.doReturn(new StorageObject(new java.io.ByteArrayInputStream(image), 0, image.length - 1, image.length, "image/png"))
                .when(storage).get(org.mockito.ArgumentMatchers.eq(key.getValue()), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        mockMvc.perform(get("/api/hackathons/" + id + "/banner").with(as(learner)))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", containsString("image/png")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mockMvc.perform(get("/api/hackathons").with(as(learner))).andExpect(jsonPath("$[0].bannerUrl").value("/api/hackathons/" + id + "/banner"));

        // Replacing removes the old file; deleting the hackathon removes the current one.
        uploadBanner(admin, id, "second.png", image).andExpect(status().isOk());
        org.mockito.Mockito.verify(storage).delete(key.getValue());
        mockMvc.perform(delete("/api/admin/hackathons/" + id).with(csrf()).with(as(admin))).andExpect(status().isNoContent());
        org.mockito.Mockito.verify(storage, org.mockito.Mockito.times(2)).delete(org.mockito.ArgumentMatchers.anyString());
    }
}
