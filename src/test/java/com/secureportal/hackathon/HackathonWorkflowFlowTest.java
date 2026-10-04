package com.secureportal.hackathon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureportal.notification.Notification;
import com.secureportal.notification.NotificationCategory;
import com.secureportal.notification.NotificationRepository;
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

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static com.secureportal.testsupport.TestPrincipals.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The parts of a hosted hackathon added on top of teams, submissions and judging: problem statements teams choose from, drafts that
 * aren't judged until turned in, certificates with a public code, and reminders sent once to teams that haven't submitted.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*local.*")
class HackathonWorkflowFlowTest {

    private static final String ADMIN = "flow-admin@example.com";
    private static final String[] TEAM_EMAILS = {"flow-a@example.com", "flow-b@example.com", "flow-c@example.com",
            "flow-d@example.com", "flow-e@example.com", "flow-f@example.com"};
    private static final String JUDGE = "flow-judge@example.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private HackathonRepository hackathonRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private HackathonDeadlineReminderJob reminderJob;
    @Autowired
    private ObjectMapper objectMapper;

    private User admin, a, b, c, d, e, f, judge;

    @BeforeEach
    void users() {
        admin = user(ADMIN, Role.ADMIN);
        a = user(TEAM_EMAILS[0], Role.VIEWER);
        b = user(TEAM_EMAILS[1], Role.VIEWER);
        c = user(TEAM_EMAILS[2], Role.VIEWER);
        d = user(TEAM_EMAILS[3], Role.VIEWER);
        e = user(TEAM_EMAILS[4], Role.VIEWER);
        f = user(TEAM_EMAILS[5], Role.VIEWER);
        judge = user(JUDGE, Role.VIEWER);
    }

    @AfterEach
    void cleanUp() {
        hackathonRepository.deleteAll();
        for (String email : List.of(ADMIN, TEAM_EMAILS[0], TEAM_EMAILS[1], TEAM_EMAILS[2], TEAM_EMAILS[3], TEAM_EMAILS[4], TEAM_EMAILS[5], JUDGE)) {
            userRepository.findByEmailIgnoreCase(email).ifPresent(userRepository::delete);
        }
    }

    private User user(String email, Role role) {
        return userRepository.findByEmailIgnoreCase(email).orElseGet(() -> userRepository.save(new User(email, email, null, role)));
    }

    // ---- Helpers -----------------------------------------------------------------------------------------------------------

    private static String at(long days) {
        return "\"" + Instant.now().plus(days, ChronoUnit.DAYS) + "\"";
    }

    /** A hosted event whose dates are given in days from now: registration closes, the build starts, the build ends. */
    private static String hosted(long regCloses, long starts, long ends) {
        return "{\"kind\":\"HOSTED\",\"title\":\"Workflow Week\",\"organizer\":\"GradientNova\",\"stream\":\"Engineering & Web Dev\","
                + "\"mode\":\"ONLINE\",\"status\":\"UPCOMING\",\"rules\":\"Build something useful.\",\"tracks\":\"Web, AI\","
                + "\"prizes\":\"Certificates\",\"minTeamSize\":1,\"maxTeamSize\":2,\"registrationDeadline\":" + at(regCloses)
                + ",\"eventStartDate\":" + at(starts) + ",\"eventEndDate\":" + at(ends) + "}";
    }

    private long createEvent() throws Exception {
        String response = mockMvc.perform(post("/api/admin/hackathons").contentType(MediaType.APPLICATION_JSON).content(hosted(1, 2, 3))
                        .with(csrf()).with(as(admin))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private void moveTimeline(long id, String body) throws Exception {
        mockMvc.perform(put("/api/admin/hackathons/" + id).contentType(MediaType.APPLICATION_JSON).content(body).with(csrf()).with(as(admin)))
                .andExpect(status().isOk());
    }

    private void createTeam(User who, long event, String name) throws Exception {
        mockMvc.perform(post("/api/hackathons/" + event + "/team").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"track\":\"Web\"}").with(csrf()).with(as(who))).andExpect(status().isOk());
    }

    private void join(User who, long event, User leader) throws Exception {
        String body = mockMvc.perform(get("/api/hackathons/" + event + "/team").with(as(leader))).andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/hackathons/join").contentType(MediaType.APPLICATION_JSON)
                .content("{\"inviteCode\":\"" + objectMapper.readTree(body).get("inviteCode").asText() + "\"}").with(csrf()).with(as(who)))
                .andExpect(status().isOk());
    }

    private ResultActions submit(User who, long event, String body) throws Exception {
        return mockMvc.perform(put("/api/hackathons/" + event + "/submission").contentType(MediaType.APPLICATION_JSON).content(body)
                .with(csrf()).with(as(who)));
    }

    private static String project(String title) {
        return "{\"title\":\"" + title + "\",\"repoUrl\":\"https://github.com/team/app\","
                + "\"description\":\"A working prototype that helps students plan their week around deadlines.\"}";
    }

    private ResultActions score(long event, long submission, int points) throws Exception {
        return mockMvc.perform(put("/api/judging/hackathons/" + event + "/submissions/" + submission + "/score")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"innovation\":" + points + ",\"execution\":" + points + ",\"impact\":" + points + ",\"presentation\":" + points + "}")
                .with(csrf()).with(as(judge)));
    }

    private ResultActions problem(long event, String body, User who) throws Exception {
        return mockMvc.perform(post("/api/admin/hackathons/" + event + "/problems").contentType(MediaType.APPLICATION_JSON).content(body)
                .with(csrf()).with(as(who)));
    }

    private long addProblem(long event, String title) throws Exception {
        String response = problem(event, "{\"title\":\"" + title + "\",\"description\":\"Build a tool that solves this properly.\"}", admin)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private ResultActions selectProblem(User who, long event, String problemId) throws Exception {
        return mockMvc.perform(put("/api/hackathons/" + event + "/team/problem").contentType(MediaType.APPLICATION_JSON)
                .content("{\"problemStatementId\":" + problemId + "}").with(csrf()).with(as(who)));
    }

    private List<Notification> hackathonNotificationsOf(User who) {
        return notificationRepository.findAll().stream()
                .filter(n -> n.getRecipientUserId().equals(who.getId()) && n.getCategory() == NotificationCategory.HACKATHON).toList();
    }

    // ---- Problem statements ------------------------------------------------------------------------------------------------

    @Test
    void organisersSetProblemStatementsAndOnlyTheTeamLeaderPicksOne() throws Exception {
        long event = createEvent();
        String base = "/api/admin/hackathons/" + event + "/problems";

        problem(event, "{\"title\":\"Bus Delays\",\"description\":\"Predict delays on campus buses.\"}", a).andExpect(status().isForbidden());
        problem(event, "{\"title\":\"ab\",\"description\":\"Predict delays on campus buses.\"}", admin).andExpect(status().isBadRequest());
        problem(event, "{\"title\":\"Bus Delays\",\"description\":\"short\"}", admin).andExpect(status().isBadRequest());
        problem(event, "{\"title\":\"Bus Delays\",\"description\":\"Predict delays on campus buses.\",\"resourcesUrl\":\"http://insecure.example.com\"}", admin)
                .andExpect(status().isBadRequest());
        long delays = addProblem(event, "Bus Delays");
        long recycling = addProblem(event, "Smarter Recycling");
        mockMvc.perform(put(base + "/" + delays).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Bus Delays v2\",\"description\":\"Predict delays on campus buses, live.\",\"track\":\"AI\"}").with(csrf()).with(as(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Bus Delays v2")).andExpect(jsonPath("$.track").value("AI"));
        mockMvc.perform(get("/api/hackathons/" + event + "/problems").with(as(a))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        createTeam(a, event, "Alpha");
        join(b, event, a);
        selectProblem(b, event, String.valueOf(delays)).andExpect(status().isForbidden());           // not the leader
        selectProblem(c, event, String.valueOf(delays)).andExpect(status().isNotFound());            // no team
        selectProblem(a, event, "999999").andExpect(status().isBadRequest());                        // not in this hackathon
        selectProblem(a, event, String.valueOf(delays)).andExpect(status().isOk()).andExpect(jsonPath("$.problemStatementId").value(delays));
        assertThat(hackathonNotificationsOf(b)).extracting(Notification::getTitle).contains("Problem Statement Selected");
        selectProblem(a, event, "null").andExpect(status().isOk()).andExpect(jsonPath("$.problemStatementId").doesNotExist());
        selectProblem(a, event, String.valueOf(recycling)).andExpect(status().isOk());

        // Deleting a problem frees the teams that chose it instead of leaving them pointing at nothing.
        mockMvc.perform(delete(base + "/" + recycling).with(csrf()).with(as(a))).andExpect(status().isForbidden());
        mockMvc.perform(delete(base + "/" + recycling).with(csrf()).with(as(admin))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/hackathons/" + event + "/team").with(as(a))).andExpect(jsonPath("$.problemStatementId").doesNotExist());
        mockMvc.perform(delete(base + "/" + recycling).with(csrf()).with(as(admin))).andExpect(status().isNotFound());

        // The choice closes with the submissions.
        moveTimeline(event, hosted(-3, -2, -1));
        selectProblem(a, event, String.valueOf(delays)).andExpect(status().isConflict());
    }

    // ---- Drafts, judging and certificates ----------------------------------------------------------------------------------

    @Test
    void draftsAreNotJudgedAndEveryRankedMemberGetsACertificateTheyAloneCanDownload() throws Exception {
        long event = createEvent();
        createTeam(a, event, "Alpha");
        join(b, event, a);
        createTeam(c, event, "Beta");
        createTeam(d, event, "Gamma");
        createTeam(e, event, "Delta");
        createTeam(f, event, "Drafty");
        mockMvc.perform(post("/api/admin/hackathons/" + event + "/judges").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + JUDGE + "\"}").with(csrf()).with(as(admin))).andExpect(status().isCreated());
        assertThat(hackathonNotificationsOf(judge)).extracting(Notification::getActionUrl).containsExactly("/judging");

        moveTimeline(event, hosted(-2, -1, 2));

        // A draft keeps whatever the team has, never invents a repository link, and cannot be undone by a second draft once submitted.
        submit(a, event, "{\"title\":\"Alpha App\",\"draft\":true}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT")).andExpect(jsonPath("$.repoUrl").doesNotExist());
        submit(a, event, "{\"title\":\"Alpha App\",\"repoUrl\":\"ftp://nope\",\"draft\":true}").andExpect(status().isBadRequest());
        submit(a, event, "{\"title\":\"Alpha App\",\"draft\":false}").andExpect(status().isBadRequest());      // a submission needs everything
        assertThat(hackathonNotificationsOf(b)).isEmpty();                                                        // drafts don't ping the team
        submit(a, event, project("Alpha App")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUBMITTED"));
        assertThat(hackathonNotificationsOf(b)).extracting(Notification::getTitle).containsExactly("Project Submitted");
        submit(a, event, "{\"title\":\"Alpha App\",\"draft\":true}").andExpect(status().isConflict());

        submit(c, event, project("Beta App")).andExpect(status().isOk());
        submit(d, event, project("Gamma App")).andExpect(status().isOk());
        submit(e, event, project("Delta App")).andExpect(status().isOk());
        submit(f, event, "{\"title\":\"Half Done\",\"draft\":true}").andExpect(status().isOk());

        moveTimeline(event, hosted(-3, -2, -1));
        // After the deadline a submitted project reads as locked, but a draft is still a draft: it was never turned in.
        mockMvc.perform(get("/api/hackathons/" + event + "/team").with(as(c))).andExpect(jsonPath("$.submission.status").value("LOCKED"));
        mockMvc.perform(get("/api/hackathons/" + event + "/team").with(as(f))).andExpect(jsonPath("$.submission.status").value("DRAFT"));

        String listing = mockMvc.perform(get("/api/judging/hackathons/" + event + "/submissions").with(as(judge))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4)).andReturn().getResponse().getContentAsString();   // the draft isn't listed
        JsonNode projects = objectMapper.readTree(listing);
        int points = 9;
        for (JsonNode project : projects) {
            score(event, project.get("submissionId").asLong(), points--).andExpect(status().isOk());          // ranks 1, 2, 3, 4 in listing order
        }

        mockMvc.perform(get("/api/hackathons/" + event + "/certificate").with(as(a))).andExpect(status().isNoContent());   // nothing before results
        mockMvc.perform(post("/api/admin/hackathons/" + event + "/publish").with(csrf()).with(as(admin))).andExpect(status().isOk());
        mockMvc.perform(get("/api/hackathons/" + event + "/results").with(as(f))).andExpect(jsonPath("$.length()").value(4));  // draft not ranked

        // Winner and her team mate, runners-up, a participant; the draft team earns nothing.
        String winnerCert = mockMvc.perform(get("/api/hackathons/" + event + "/certificate").with(as(a))).andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("WINNER")).andExpect(jsonPath("$.teamName").value("Alpha"))
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.startsWith("HACK-"))).andReturn().getResponse().getContentAsString();
        mockMvc.perform(get("/api/hackathons/" + event + "/certificate").with(as(b))).andExpect(jsonPath("$.type").value("WINNER"));
        mockMvc.perform(get("/api/hackathons/" + event + "/certificate").with(as(c))).andExpect(jsonPath("$.type").value("RUNNER_UP"));
        mockMvc.perform(get("/api/hackathons/" + event + "/certificate").with(as(d))).andExpect(jsonPath("$.type").value("RUNNER_UP"));
        mockMvc.perform(get("/api/hackathons/" + event + "/certificate").with(as(e))).andExpect(jsonPath("$.type").value("PARTICIPATION"));
        mockMvc.perform(get("/api/hackathons/" + event + "/certificate").with(as(f))).andExpect(status().isNoContent());
        assertThat(hackathonNotificationsOf(a)).extracting(Notification::getTitle).contains("Certificate Available", "Results Published");
        assertThat(hackathonNotificationsOf(f)).extracting(Notification::getTitle).doesNotContain("Certificate Available");

        // The PDF is the signed-in member's own: others get nothing, and nobody can fetch it by guessing a code.
        byte[] pdf = mockMvc.perform(get("/api/hackathons/" + event + "/certificate/pdf").with(as(a))).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF)).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        mockMvc.perform(get("/api/hackathons/" + event + "/certificate/pdf").with(as(f))).andExpect(status().isNotFound());
        String code = objectMapper.readTree(winnerCert).get("code").asText();
        mockMvc.perform(get("/api/hackathons/certificates/code/" + code).with(as(f))).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/hackathons/certificates/code/" + code + "/pdf")).andExpect(status().is3xxRedirection());

        // Anyone holding the code can verify who earned what, and learns nothing else.
        mockMvc.perform(get("/api/certificates/verify/" + code.toLowerCase())).andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true)).andExpect(jsonPath("$.kind").value("HACKATHON"))
                .andExpect(jsonPath("$.award").value("WINNER")).andExpect(jsonPath("$.recipientName").value(TEAM_EMAILS[0]))
                .andExpect(jsonPath("$.courseTitle").value("Workflow Week")).andExpect(jsonPath("$.teamName").doesNotExist());
        mockMvc.perform(get("/api/certificates/verify/HACK-NOPENOPENOPE")).andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(false));

        // Problem statements are frozen once results are out.
        problem(event, "{\"title\":\"Too Late\",\"description\":\"Nobody can pick this any more.\"}", admin).andExpect(status().isConflict());
    }

    // ---- Reminders ---------------------------------------------------------------------------------------------------------

    @Test
    void deadlineRemindersGoOnceAndOnlyToTeamsThatHaveNotSubmitted() throws Exception {
        long event = createEvent();
        createTeam(a, event, "Alpha");
        join(b, event, a);
        createTeam(c, event, "Beta");
        moveTimeline(event, hosted(-2, -1, 2));
        submit(c, event, project("Beta App")).andExpect(status().isOk());
        submit(a, event, "{\"title\":\"Alpha App\",\"draft\":true}").andExpect(status().isOk());   // a draft is not turned in
        Instant deadline = hackathonRepository.findById(event).orElseThrow().getEventEndDate();
        notificationRepository.deleteAll();                                                         // forget the join notices

        reminderJob.remindDue(deadline.minus(Duration.ofHours(30)));                                // too early: nothing yet
        assertThat(hackathonNotificationsOf(a)).isEmpty();

        reminderJob.remindDue(deadline.minus(Duration.ofHours(20)));
        reminderJob.remindDue(deadline.minus(Duration.ofHours(19)));                                // the same reminder never repeats
        assertThat(hackathonNotificationsOf(a)).extracting(Notification::getTitle).containsExactly("Submission Deadline Approaching");
        assertThat(hackathonNotificationsOf(b)).hasSize(1);
        assertThat(hackathonNotificationsOf(c)).isEmpty();                                          // already submitted

        reminderJob.remindDue(deadline.minus(Duration.ofMinutes(30)));
        reminderJob.remindDue(deadline.minus(Duration.ofMinutes(10)));
        assertThat(hackathonNotificationsOf(a)).extracting(Notification::getTitle)
                .containsExactlyInAnyOrder("Submission Deadline Approaching", "1 Hour Left to Submit");
        assertThat(hackathonNotificationsOf(c)).isEmpty();

        reminderJob.remindDue(deadline.plus(Duration.ofMinutes(1)));                                // closed: no more reminders
        assertThat(hackathonNotificationsOf(a)).hasSize(2);
    }
}
