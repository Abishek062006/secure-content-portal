package com.secureportal.hackathon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureportal.audit.AuditRepository;
import com.secureportal.gamification.GamificationService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        fields.put("pointsReward", "25");
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
        createRejected(hackathon("pointsReward", "5000"), "between 0 and");
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
    void registeringPaysItsPointsOnceAndOnlyLearnersMayRegister() throws Exception {
        long id = create(hackathon("pointsReward", "30"));

        mockMvc.perform(get("/api/hackathons").with(as(learner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].isRegistered").value(false)).andExpect(jsonPath("$[0].participantCount").value(0));

        int before = gamification.summary(learner.getId()).totalPoints();
        mockMvc.perform(post("/api/hackathons/" + id + "/register").with(csrf()).with(as(learner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.alreadyRegistered").value(false)).andExpect(jsonPath("$.pointsEarned").value(30))
                .andExpect(jsonPath("$.registrationUrl").value("https://example.com/sprint"));
        assertThat(gamification.summary(learner.getId()).totalPoints()).isEqualTo(before + 30);

        mockMvc.perform(post("/api/hackathons/" + id + "/register").with(csrf()).with(as(learner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.alreadyRegistered").value(true)).andExpect(jsonPath("$.pointsEarned").value(0));
        assertThat(gamification.summary(learner.getId()).totalPoints()).isEqualTo(before + 30);

        mockMvc.perform(get("/api/hackathons").with(as(learner)))
                .andExpect(jsonPath("$[0].isRegistered").value(true)).andExpect(jsonPath("$[0].participantCount").value(1));

        mockMvc.perform(post("/api/hackathons/" + id + "/register").with(csrf()).with(as(admin))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/hackathons/999999/register").with(csrf()).with(as(learner))).andExpect(status().isNotFound());
    }

    @Test
    void finishedHackathonsAndPastDeadlinesRefuseRegistration() throws Exception {
        long finished = create(hackathon("status", "\"COMPLETED\""));
        long pastDeadline = create(hackathon("registrationDeadline", "\"2020-01-01T00:00:00Z\""));

        mockMvc.perform(post("/api/hackathons/" + finished + "/register").with(csrf()).with(as(learner)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value(containsString("closed")));
        mockMvc.perform(post("/api/hackathons/" + pastDeadline + "/register").with(csrf()).with(as(learner))).andExpect(status().isConflict());
        assertThat(gamification.summary(learner.getId()).totalPoints()).isZero();
    }

    @Test
    void deletingAHackathonRemovesItsRegistrations() throws Exception {
        long id = create(hackathon());
        mockMvc.perform(post("/api/hackathons/" + id + "/register").with(csrf()).with(as(learner))).andExpect(status().isOk());
        mockMvc.perform(delete("/api/admin/hackathons/" + id).with(csrf()).with(as(admin))).andExpect(status().isNoContent());
        // Nothing left behind: a new hackathon is registered for from scratch.
        create(hackathon());
        mockMvc.perform(get("/api/hackathons").with(as(learner)))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].participantCount").value(0));
    }
}
