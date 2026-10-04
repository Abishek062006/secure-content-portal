package com.secureportal.network;

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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static com.secureportal.testsupport.TestPrincipals.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Connecting with other members: nobody can act on a request that isn't theirs, one relationship exists per pair however it was
 * asked for, and no email address ever leaves the server.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*local.*")
class NetworkFlowTest {

    private static final String ADMIN = "network-admin@example.com";
    private static final String ANA = "network-ana@example.com";
    private static final String BEN = "network-ben@example.com";
    private static final String CARA = "network-cara@example.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ConnectionRepository connectionRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private User admin;
    private User ana;
    private User ben;
    private User cara;

    @BeforeEach
    void users() {
        admin = user(ADMIN, "Network Admin", Role.ADMIN);
        ana = user(ANA, "Ana Network", Role.VIEWER);
        ben = user(BEN, "Ben Network", Role.VIEWER);
        cara = user(CARA, "Cara Network", Role.VIEWER);
    }

    @AfterEach
    void cleanUp() {
        // Deleting the users removes their connections and notifications with them.
        for (String email : List.of(ADMIN, ANA, BEN, CARA)) {
            userRepository.findByEmailIgnoreCase(email).ifPresent(userRepository::delete);
        }
    }

    private User user(String email, String name, Role role) {
        return userRepository.findByEmailIgnoreCase(email).orElseGet(() -> userRepository.save(new User(email, name, null, role)));
    }

    private ResultActions request(User from, User to) throws Exception {
        return mockMvc.perform(post("/api/network/requests/" + to.getId()).with(csrf()).with(as(from)));
    }

    private long requestId(User from, User to) throws Exception {
        String body = request(from, to).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private void connect(User a, User b) throws Exception {
        long id = requestId(a, b);
        mockMvc.perform(post("/api/network/requests/" + id + "/accept").with(csrf()).with(as(b))).andExpect(status().isOk());
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    @Test
    void aRequestIsSentAcceptedAndListedForBothPeople() throws Exception {
        long id = requestId(ana, ben);

        mockMvc.perform(get("/api/network/requests/received").with(as(ben)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].requesterName").value("Ana Network"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
        mockMvc.perform(get("/api/network/requests/sent").with(as(ana)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].receiverName").value("Ben Network"));
        mockMvc.perform(get("/api/network/connections/count").with(as(ana))).andExpect(jsonPath("$.count").value(0));

        mockMvc.perform(post("/api/network/requests/" + id + "/accept").with(csrf()).with(as(ben)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
        // Accepting again changes nothing.
        mockMvc.perform(post("/api/network/requests/" + id + "/accept").with(csrf()).with(as(ben)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACCEPTED"));

        mockMvc.perform(get("/api/network/connections").with(as(ana)))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].displayName").value("Ben Network"));
        mockMvc.perform(get("/api/network/connections").with(as(ben)))
                .andExpect(jsonPath("$[0].displayName").value("Ana Network"));
        mockMvc.perform(get("/api/network/connections/count").with(as(ben))).andExpect(jsonPath("$.count").value(1));
        mockMvc.perform(get("/api/network/requests/received").with(as(ben))).andExpect(jsonPath("$.length()").value(0));

        // Each of them was told.
        mockMvc.perform(get("/api/notifications").param("category", "CONNECTION").with(as(ben)))
                .andExpect(jsonPath("$.content[0].title").value("New connection request"));
        mockMvc.perform(get("/api/notifications").param("category", "CONNECTION").with(as(ana)))
                .andExpect(jsonPath("$.content[0].title").value("Connection request accepted"));
    }

    @Test
    void noEmailOrRoleIsEverReturnedAboutAnotherMember() throws Exception {
        long id = requestId(ana, ben);
        connect(cara, ana);

        String[] bodies = {
                mockMvc.perform(get("/api/network/suggestions").with(as(ana))).andReturn().getResponse().getContentAsString(),
                mockMvc.perform(get("/api/network/requests/received").with(as(ben))).andReturn().getResponse().getContentAsString(),
                mockMvc.perform(get("/api/network/requests/sent").with(as(ana))).andReturn().getResponse().getContentAsString(),
                mockMvc.perform(get("/api/network/connections").with(as(ana))).andReturn().getResponse().getContentAsString(),
                mockMvc.perform(post("/api/network/requests/" + id + "/accept").with(csrf()).with(as(ben))).andReturn().getResponse().getContentAsString(),
        };
        for (String body : bodies) {
            assertThat(body).doesNotContain("@example.com").doesNotContainIgnoringCase("\"email\"").doesNotContain("\"role\"");
        }
    }

    @Test
    void peopleAreFoundByNameOnlyAndNeverByEmailOrAsAdmins() throws Exception {
        mockMvc.perform(get("/api/network/suggestions").param("q", "ben net").with(as(ana)))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].displayName").value("Ben Network"))
                .andExpect(jsonPath("$.content[0].relationshipStatus").value("NONE"));
        // An address is not a way to find (or confirm) someone.
        mockMvc.perform(get("/api/network/suggestions").param("q", "network-ben@").with(as(ana)))
                .andExpect(jsonPath("$.content.length()").value(0));

        JsonNode all = json(mockMvc.perform(get("/api/network/suggestions").param("q", "network").param("size", "50").with(as(ana))));
        List<String> names = all.get("content").findValuesAsText("displayName");
        assertThat(names).contains("Ben Network", "Cara Network").doesNotContain("Ana Network", "Network Admin");
    }

    @Test
    void suggestionsShowHowYouStandWithEachPersonAndWhoYouShare() throws Exception {
        connect(ana, cara);
        connect(ben, cara); // Ana and Ben share Cara
        requestId(ana, ben);

        JsonNode forAna = json(mockMvc.perform(get("/api/network/suggestions").param("q", "network").with(as(ana))));
        JsonNode benRow = rowOf(forAna, "Ben Network");
        JsonNode caraRow = rowOf(forAna, "Cara Network");
        assertThat(benRow.get("relationshipStatus").asText()).isEqualTo("PENDING_SENT");
        assertThat(benRow.get("mutualConnectionsCount").asInt()).isEqualTo(1);
        assertThat(caraRow.get("relationshipStatus").asText()).isEqualTo("ACCEPTED");

        JsonNode forBen = json(mockMvc.perform(get("/api/network/suggestions").param("q", "network").with(as(ben))));
        assertThat(rowOf(forBen, "Ana Network").get("relationshipStatus").asText()).isEqualTo("PENDING_RECEIVED");
    }

    private static JsonNode rowOf(JsonNode page, String name) {
        for (JsonNode row : page.get("content")) {
            if (row.get("displayName").asText().equals(name)) {
                return row;
            }
        }
        throw new AssertionError("not listed: " + name);
    }

    @Test
    void nobodyCanActOnARequestThatIsNotTheirsAndItLooksLikeItDoesNotExist() throws Exception {
        long id = requestId(ana, ben);

        // A stranger, the sender (who can't accept their own request) and the receiver (who can't withdraw it) all get "not found".
        for (User who : List.of(cara, ana)) {
            mockMvc.perform(post("/api/network/requests/" + id + "/accept").with(csrf()).with(as(who))).andExpect(status().isNotFound());
        }
        for (User who : List.of(cara, ana)) {
            mockMvc.perform(post("/api/network/requests/" + id + "/reject").with(csrf()).with(as(who))).andExpect(status().isNotFound());
        }
        for (User who : List.of(cara, ben)) {
            mockMvc.perform(delete("/api/network/requests/" + id).with(csrf()).with(as(who))).andExpect(status().isNotFound());
        }
        mockMvc.perform(post("/api/network/requests/999999/accept").with(csrf()).with(as(ben))).andExpect(status().isNotFound());
        assertThat(connectionRepository.findById(id)).isPresent();

        mockMvc.perform(delete("/api/network/requests/" + id).with(csrf()).with(as(ana))).andExpect(status().isNoContent());
        assertThat(connectionRepository.findById(id)).isEmpty();
    }

    @Test
    void decliningRemovesTheRequestAndAnAcceptedConnectionCannotBeDeclined() throws Exception {
        long id = requestId(ana, ben);
        mockMvc.perform(post("/api/network/requests/" + id + "/reject").with(csrf()).with(as(ben))).andExpect(status().isOk());
        assertThat(connectionRepository.findById(id)).isEmpty();
        // Asking again is allowed once it has been declined.
        long again = requestId(ana, ben);
        mockMvc.perform(post("/api/network/requests/" + again + "/accept").with(csrf()).with(as(ben))).andExpect(status().isOk());
        mockMvc.perform(post("/api/network/requests/" + again + "/reject").with(csrf()).with(as(ben))).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/network/requests/" + again).with(csrf()).with(as(ana))).andExpect(status().isNotFound());
    }

    @Test
    void impossibleRequestsAreRefusedWithAClearReason() throws Exception {
        request(ana, ana).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value(containsString("yourself")));
        request(ana, admin).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value(containsString("other learners")));
        request(admin, ana).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/network/requests/999999").with(csrf()).with(as(ana))).andExpect(status().isNotFound());

        request(ana, ben).andExpect(status().isCreated());
        request(ana, ben).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value(containsString("already sent")));
        request(ben, ana).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value(containsString("already sent you a request")));
    }

    @Test
    void anAlreadyConnectedPairCannotRequestAgainAndEitherSideCanEndIt() throws Exception {
        connect(ana, ben);
        request(ana, ben).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value(containsString("already connected")));
        request(ben, ana).andExpect(status().isConflict());

        mockMvc.perform(delete("/api/network/connections/" + ana.getId()).with(csrf()).with(as(ben))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/network/connections/count").with(as(ana))).andExpect(jsonPath("$.count").value(0));
        // Ending one that isn't there is not an error.
        mockMvc.perform(delete("/api/network/connections/" + ana.getId()).with(csrf()).with(as(ben))).andExpect(status().isNoContent());
        // A pending request is not a connection, so removing "the connection" leaves it alone.
        long id = requestId(ana, cara);
        mockMvc.perform(delete("/api/network/connections/" + ana.getId()).with(csrf()).with(as(cara))).andExpect(status().isNoContent());
        assertThat(connectionRepository.findById(id)).isPresent();
    }

    @Test
    void theDatabaseAllowsOnlyOneRelationshipPerPairWhicheverWayTheyAsked() {
        connectionRepository.saveAndFlush(new Connection(ana.getId(), ben.getId()));

        assertThatThrownBy(() -> connectionRepository.saveAndFlush(new Connection(ben.getId(), ana.getId())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> connectionRepository.saveAndFlush(new Connection(ana.getId(), ben.getId())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> connectionRepository.saveAndFlush(new Connection(ana.getId(), ana.getId())))
                .as("nobody connects with themselves").hasMessageContaining("connections_not_self_check");
    }

    @Test
    void connectionsAreOnlyForSignedInMembers() throws Exception {
        // Signed-out visitors are sent to sign in rather than shown anything.
        mockMvc.perform(get("/api/network/connections")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/api/network/suggestions")).andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/api/network/requests/" + ben.getId()).with(csrf())).andExpect(status().is3xxRedirection());
    }
}
