package com.secureportal.hackathon;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static com.secureportal.testsupport.TestPrincipals.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A hosted hackathon end to end: teams form only while registration is open, projects are submitted only while the build runs, judges
 * score only after it closes, and results appear only when an admin publishes them with every project scored.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*local.*")
class HostedHackathonFlowTest {

    private static final String ADMIN = "hosted-admin@example.com";
    private static final String A = "hosted-a@example.com";
    private static final String B = "hosted-b@example.com";
    private static final String C = "hosted-c@example.com";
    private static final String D = "hosted-d@example.com";
    private static final String JUDGE = "hosted-judge@example.com";
    private static final List<String> EMAILS = List.of(ADMIN, A, B, C, D, JUDGE);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private HackathonRepository hackathonRepository;
    @Autowired
    private AuditRepository auditRepository;
    @Autowired
    private GamificationService gamification;
    @Autowired
    private ObjectMapper objectMapper;

    private User admin, a, b, c, d, judge;

    @BeforeEach
    void users() {
        admin = user(ADMIN, Role.ADMIN);
        a = user(A, Role.VIEWER);
        b = user(B, Role.VIEWER);
        c = user(C, Role.VIEWER);
        d = user(D, Role.VIEWER);
        judge = user(JUDGE, Role.VIEWER);
    }

    @AfterEach
    void cleanUp() {
        hackathonRepository.deleteAll();
        for (String email : EMAILS) {
            userRepository.findByEmailIgnoreCase(email).ifPresent(userRepository::delete);
        }
    }

    private User user(String email, Role role) {
        return userRepository.findByEmailIgnoreCase(email).orElseGet(() -> userRepository.save(new User(email, email, null, role)));
    }

    // ---- Building an event ------------------------------------------------------------------------------------------------

    private static String at(long days) {
        return "\"" + Instant.now().plus(days, ChronoUnit.DAYS) + "\"";
    }

    /** A hosted event whose dates are given in days from now: registration closes, the build starts, the build ends. */
    private static String hosted(long regCloses, long starts, long ends, String... overrides) {
        java.util.Map<String, String> f = new java.util.LinkedHashMap<>();
        f.put("kind", "\"HOSTED\"");
        f.put("title", "\"Campus Build Week\"");
        f.put("organizer", "\"GradientNova\"");
        f.put("stream", "\"Engineering & Web Dev\"");
        f.put("mode", "\"ONLINE\"");
        f.put("status", "\"UPCOMING\"");
        f.put("rules", "\"Build something useful. Original work only.\"");
        f.put("tracks", "\"Web, AI\"");
        f.put("prizes", "\"Certificates and swag\"");
        f.put("minTeamSize", "1");
        f.put("maxTeamSize", "2");
        f.put("registrationDeadline", at(regCloses));
        f.put("eventStartDate", at(starts));
        f.put("eventEndDate", at(ends));
        for (int i = 0; i < overrides.length; i += 2) f.put(overrides[i], overrides[i + 1]);
        StringBuilder json = new StringBuilder("{");
        f.forEach((k, v) -> json.append(json.length() > 1 ? "," : "").append('"').append(k).append("\":").append(v));
        return json.append('}').toString();
    }

    private long createEvent(String body) throws Exception {
        String response = mockMvc.perform(post("/api/admin/hackathons").contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(csrf()).with(as(admin))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private void moveTimeline(long id, String body) throws Exception {
        mockMvc.perform(put("/api/admin/hackathons/" + id).contentType(MediaType.APPLICATION_JSON).content(body).with(csrf()).with(as(admin)))
                .andExpect(status().isOk());
    }

    private ResultActions createTeam(User who, long event, String name, String track) throws Exception {
        return mockMvc.perform(post("/api/hackathons/" + event + "/team").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"track\":\"" + track + "\"}").with(csrf()).with(as(who)));
    }

    private String inviteCode(User who, long event) throws Exception {
        String body = mockMvc.perform(get("/api/hackathons/" + event + "/team").with(as(who))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("inviteCode").asText();
    }

    private ResultActions join(User who, String code) throws Exception {
        return mockMvc.perform(post("/api/hackathons/join").contentType(MediaType.APPLICATION_JSON)
                .content("{\"inviteCode\":\"" + code + "\"}").with(csrf()).with(as(who)));
    }

    private static final String PROJECT = "{\"title\":\"Bus Planner\",\"repoUrl\":\"https://github.com/team/bus\","
            + "\"demoUrl\":\"https://youtu.be/abc\",\"description\":\"A route planner that helps students find the fastest bus.\"}";

    private ResultActions submit(User who, long event, String body) throws Exception {
        return mockMvc.perform(put("/api/hackathons/" + event + "/submission").contentType(MediaType.APPLICATION_JSON).content(body)
                .with(csrf()).with(as(who)));
    }

    private ResultActions score(User who, long event, long submission, int i, int e, int im, int p) throws Exception {
        return mockMvc.perform(put("/api/judging/hackathons/" + event + "/submissions/" + submission + "/score")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"innovation\":" + i + ",\"execution\":" + e + ",\"impact\":" + im + ",\"presentation\":" + p + ",\"comment\":\"Nice\"}")
                .with(csrf()).with(as(who)));
    }

    // ---- Tests ------------------------------------------------------------------------------------------------------------

    @Test
    void aHostedEventNeedsItsWholeTimelineRulesAndSaneTeamSizes() throws Exception {
        String url = "/api/admin/hackathons";
        for (String bad : List.of(
                hosted(1, 2, 3, "registrationDeadline", "null"),
                hosted(3, 2, 4),                                  // registration closes after the build starts
                hosted(1, 3, 2),                                  // ends before it starts
                hosted(1, 2, 3, "rules", "\"\""),
                hosted(1, 2, 3, "minTeamSize", "0"),
                hosted(1, 2, 3, "minTeamSize", "3", "maxTeamSize", "2"),
                hosted(1, 2, 3, "maxTeamSize", "11"),
                hosted(1, 2, 3, "tracks", "\"a,b,c,d,e,f,g,h,i\""),
                hosted(1, 2, 3, "kind", "\"SPACESHIP\""))) {
            mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(bad).with(csrf()).with(as(admin))).andExpect(status().isBadRequest());
        }
        assertThat(hackathonRepository.count()).isZero();

        long id = createEvent(hosted(1, 2, 3));
        mockMvc.perform(get("/api/hackathons/" + id).with(as(a)))
                .andExpect(jsonPath("$.kind").value("HOSTED")).andExpect(jsonPath("$.phase").value("REGISTRATION"))
                .andExpect(jsonPath("$.registrationOpen").value(true)).andExpect(jsonPath("$.tracks").value("Web, AI"));
        // An event can't turn into the other kind, and a learner can't create one.
        mockMvc.perform(put(url + "/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"x\",\"stream\":\"Web\",\"mode\":\"ONLINE\",\"status\":\"ACTIVE\",\"registrationUrl\":\"https://example.com/x\"}")
                .with(csrf()).with(as(admin))).andExpect(status().isBadRequest());
        mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(hosted(1, 2, 3)).with(csrf()).with(as(a))).andExpect(status().isForbidden());
    }

    @Test
    void teamsFormWithInviteCodesUpToTheirSizeAndOnlyWhileRegistrationIsOpen() throws Exception {
        long event = createEvent(hosted(1, 2, 3));

        createTeam(a, event, "!", "Web").andExpect(status().isBadRequest());                 // name too short/plain
        createTeam(a, event, "Bus Crew", "Robotics").andExpect(status().isBadRequest());     // not one of the tracks
        createTeam(admin, event, "Admin Crew", "Web").andExpect(status().isForbidden());
        createTeam(a, event, "Bus Crew", "Web").andExpect(status().isOk()).andExpect(jsonPath("$.leader").value(true))
                .andExpect(jsonPath("$.members.length()").value(1)).andExpect(jsonPath("$.inviteCode").exists());
        createTeam(a, event, "Second Crew", "Web").andExpect(status().isConflict());         // already on a team
        createTeam(b, event, "Bus Crew", "Web").andExpect(status().isConflict());           // name taken

        String code = inviteCode(a, event);
        assertThat(code).hasSize(10);
        join(b, "WRONGCODE1").andExpect(status().isBadRequest());
        join(b, code).andExpect(status().isOk()).andExpect(jsonPath("$.members.length()").value(2));
        join(b, code).andExpect(status().isOk());                                            // joining again changes nothing
        join(c, code).andExpect(status().isConflict()).andExpect(content().string(containsString("full")));

        // Only team members see the invite code, and someone with no team gets nothing back.
        mockMvc.perform(get("/api/hackathons/" + event + "/team").with(as(c))).andExpect(status().isNoContent());
        createTeam(c, event, "Solo", "AI").andExpect(status().isOk());
        join(c, code).andExpect(status().isConflict());                                      // one team at a time

        // Leaving: the leader role passes on, an empty team disappears, and the freed place can be taken.
        mockMvc.perform(delete("/api/hackathons/" + event + "/team").with(csrf()).with(as(a))).andExpect(status().isOk());
        mockMvc.perform(get("/api/hackathons/" + event + "/team").with(as(b))).andExpect(jsonPath("$.leader").value(true))
                .andExpect(jsonPath("$.members.length()").value(1));
        mockMvc.perform(delete("/api/hackathons/" + event + "/team").with(csrf()).with(as(b))).andExpect(status().isOk());
        join(a, code).andExpect(status().isBadRequest());                                    // that team is gone, so its code is dead

        // After the registration deadline nothing new can form.
        moveTimeline(event, hosted(-1, 2, 3));
        createTeam(d, event, "Late Crew", "Web").andExpect(status().isConflict());
        join(d, inviteCode(c, event)).andExpect(status().isConflict());
    }

    @Test
    void projectsAreSubmittedOnlyWhileTheBuildRunsByTeamsBigEnoughAndWithSafeLinks() throws Exception {
        long event = createEvent(hosted(1, 2, 3, "minTeamSize", "2"));
        createTeam(a, event, "Bus Crew", "Web").andExpect(status().isOk());
        join(b, inviteCode(a, event)).andExpect(status().isOk());
        createTeam(c, event, "Solo Crew", "AI").andExpect(status().isOk());

        submit(a, event, PROJECT).andExpect(status().isConflict());                          // the build hasn't started

        moveTimeline(event, hosted(-2, -1, 2, "minTeamSize", "2"));
        submit(d, event, PROJECT).andExpect(status().isConflict());                          // no team
        submit(c, event, PROJECT).andExpect(status().isConflict());                          // team too small
        submit(a, event, PROJECT.replace("https://github.com/team/bus", "http://github.com/team/bus")).andExpect(status().isBadRequest());
        submit(a, event, PROJECT.replace("https://github.com/team/bus", "javascript:alert(1)")).andExpect(status().isBadRequest());
        submit(a, event, PROJECT.replace("A route planner that helps students find the fastest bus.", "Too short")).andExpect(status().isBadRequest());
        submit(admin, event, PROJECT).andExpect(status().isForbidden());

        submit(a, event, PROJECT).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Bus Planner"));
        // A team mate can improve it, and the team still has one project.
        submit(b, event, PROJECT.replace("Bus Planner", "Bus Planner 2")).andExpect(status().isOk());
        mockMvc.perform(get("/api/hackathons/" + event + "/team").with(as(a))).andExpect(jsonPath("$.submission.title").value("Bus Planner 2"));

        // Teams are locked once the build has started, and submissions close at the deadline.
        mockMvc.perform(delete("/api/hackathons/" + event + "/team").with(csrf()).with(as(a))).andExpect(status().isConflict());
        moveTimeline(event, hosted(-3, -2, -1, "minTeamSize", "2"));
        submit(a, event, PROJECT).andExpect(status().isConflict());
    }

    @Test
    void judgesScoreOnlyAfterSubmissionsCloseAndResultsPublishOnlyWhenEverythingIsScored() throws Exception {
        long event = createEvent(hosted(1, 2, 3));
        createTeam(a, event, "Alpha", "Web").andExpect(status().isOk());
        createTeam(b, event, "Beta", "AI").andExpect(status().isOk());
        createTeam(c, event, "Gamma", "Web").andExpect(status().isOk());

        // Judges: known people who aren't competing.
        String judgesUrl = "/api/admin/hackathons/" + event + "/judges";
        mockMvc.perform(post(judgesUrl).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"nobody@example.com\"}").with(csrf()).with(as(admin)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(judgesUrl).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + A + "\"}").with(csrf()).with(as(admin)))
                .andExpect(status().isConflict());                                           // a competitor can't judge
        mockMvc.perform(post(judgesUrl).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + JUDGE + "\"}").with(csrf()).with(as(a)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(judgesUrl).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + JUDGE + "\"}").with(csrf()).with(as(admin)))
                .andExpect(status().isCreated());
        mockMvc.perform(post(judgesUrl).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + JUDGE + "\"}").with(csrf()).with(as(admin)))
                .andExpect(status().isConflict());
        createTeam(judge, event, "Judges Crew", "Web").andExpect(status().isConflict());     // a judge can't enter

        moveTimeline(event, hosted(-2, -1, 2));
        submit(a, event, PROJECT.replace("Bus Planner", "Alpha App")).andExpect(status().isOk());
        submit(b, event, PROJECT.replace("Bus Planner", "Beta App")).andExpect(status().isOk());
        submit(c, event, PROJECT.replace("Bus Planner", "Gamma App")).andExpect(status().isOk());

        // While the build runs there is nothing to judge yet.
        mockMvc.perform(get("/api/judging/hackathons/" + event + "/submissions").with(as(judge))).andExpect(status().isConflict());
        mockMvc.perform(get("/api/judging/events").with(as(judge))).andExpect(jsonPath("$.length()").value(0));

        moveTimeline(event, hosted(-3, -2, -1));
        mockMvc.perform(get("/api/judging/events").with(as(judge))).andExpect(jsonPath("$[0].phase").value("JUDGING"));
        mockMvc.perform(get("/api/judging/hackathons/" + event + "/submissions").with(as(a))).andExpect(status().isForbidden()); // not a judge
        String listing = mockMvc.perform(get("/api/judging/hackathons/" + event + "/submissions").with(as(judge))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3)).andReturn().getResponse().getContentAsString();
        JsonNode projects = objectMapper.readTree(listing);
        long alpha = projects.get(0).get("submissionId").asLong(), beta = projects.get(1).get("submissionId").asLong(),
                gamma = projects.get(2).get("submissionId").asLong();

        // Publishing needs every project scored.
        String publish = "/api/admin/hackathons/" + event + "/publish";
        mockMvc.perform(post(publish).with(csrf()).with(as(admin))).andExpect(status().isConflict());
        mockMvc.perform(get("/api/hackathons/" + event + "/results").with(as(a))).andExpect(status().isConflict());

        score(judge, event, alpha, 0, 5, 5, 5).andExpect(status().isBadRequest());
        score(judge, event, alpha, 5, 5, 5, 11).andExpect(status().isBadRequest());
        score(a, event, alpha, 5, 5, 5, 5).andExpect(status().isForbidden());
        score(judge, event, 999999, 5, 5, 5, 5).andExpect(status().isNotFound());
        score(judge, event, alpha, 9, 8, 9, 8).andExpect(status().isOk());   // 8.5
        score(judge, event, beta, 6, 6, 6, 6).andExpect(status().isOk());    // 6.0
        score(judge, event, beta, 7, 7, 7, 7).andExpect(status().isOk());    // rescoring replaces: 7.0
        mockMvc.perform(post(publish).with(csrf()).with(as(admin))).andExpect(status().isConflict()); // Gamma is still unscored
        score(judge, event, gamma, 7, 7, 7, 7).andExpect(status().isOk());   // 7.0, ties with Beta

        mockMvc.perform(get("/api/admin/hackathons/" + event + "/submissions").with(as(a))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/hackathons/" + event + "/submissions").with(as(admin))).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].scoreCount").value(1));

        mockMvc.perform(post(publish).with(csrf()).with(as(a))).andExpect(status().isForbidden());
        mockMvc.perform(post(publish).with(csrf()).with(as(admin))).andExpect(status().isOk());
        mockMvc.perform(post(publish).with(csrf()).with(as(admin))).andExpect(status().isConflict()); // already published

        mockMvc.perform(get("/api/hackathons/" + event + "/results").with(as(d)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].teamName").value("Alpha")).andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[0].overall").value(8.5))
                .andExpect(jsonPath("$[1].rank").value(2)).andExpect(jsonPath("$[2].rank").value(2)) // a tie shares a rank
                .andExpect(jsonPath("$[0].comment").doesNotExist());
        mockMvc.perform(get("/api/hackathons/" + event).with(as(d))).andExpect(jsonPath("$.phase").value("RESULTS"));

        // Everyone who submitted earns the builder badge; the top three also earn the winner badge.
        assertThat(gamification.badgesOf(a.getId())).filteredOn(x -> x.unlocked()).extracting("id").contains("HACKATHON_BUILDER", "HACKATHON_WINNER");
        assertThat(gamification.badgesOf(d.getId())).filteredOn(x -> x.unlocked()).isEmpty();

        // Nothing can be changed after publishing.
        score(judge, event, alpha, 1, 1, 1, 1).andExpect(status().isConflict());
        mockMvc.perform(post(judgesUrl).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + D + "\"}").with(csrf()).with(as(admin)))
                .andExpect(status().isConflict());
        assertThat(auditRepository.findAll()).extracting("action").contains("HACKATHON_CREATE", "HACKATHON_JUDGE_ADD", "HACKATHON_PUBLISH_RESULTS");
    }

    @Test
    void anExternalListingHasNoTeamsOrResults() throws Exception {
        String external = "{\"title\":\"Elsewhere Jam\",\"stream\":\"Web\",\"mode\":\"ONLINE\",\"status\":\"ACTIVE\",\"registrationUrl\":\"https://example.com/jam\"}";
        String response = mockMvc.perform(post("/api/admin/hackathons").contentType(MediaType.APPLICATION_JSON).content(external).with(csrf()).with(as(admin)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(response).get("id").asLong();
        createTeam(a, id, "Crew", "Web").andExpect(status().isNotFound());
        mockMvc.perform(get("/api/hackathons/" + id + "/results").with(as(a))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/hackathons/" + id).with(as(a))).andExpect(jsonPath("$.kind").value("EXTERNAL")).andExpect(jsonPath("$.phase").doesNotExist());
    }
}
