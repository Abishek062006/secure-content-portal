package com.secureportal.messaging;

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
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static com.secureportal.testsupport.TestPrincipals.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Messaging is for connected members only, a conversation belongs to its two members, and no email address is ever shown. */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*local.*")
class MessagingFlowTest {

    private static final String ADMIN = "messaging-admin@example.com";
    private static final String ANA = "messaging-ana@example.com";
    private static final String BEN = "messaging-ben@example.com";
    private static final String CARA = "messaging-cara@example.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private User admin;
    private User ana;
    private User ben;
    private User cara;

    @BeforeEach
    void users() {
        admin = user(ADMIN, "Messaging Admin", Role.ADMIN);
        ana = user(ANA, "Ana Messaging", Role.VIEWER);
        ben = user(BEN, "Ben Messaging", Role.VIEWER);
        cara = user(CARA, "Cara Messaging", Role.VIEWER);
    }

    @AfterEach
    void cleanUp() {
        // Deleting the users removes their connections, conversations and messages with them.
        for (String email : List.of(ADMIN, ANA, BEN, CARA)) {
            userRepository.findByEmailIgnoreCase(email).ifPresent(userRepository::delete);
        }
    }

    private User user(String email, String name, Role role) {
        return userRepository.findByEmailIgnoreCase(email).orElseGet(() -> userRepository.save(new User(email, name, null, role)));
    }

    private void connect(User a, User b) throws Exception {
        String sent = mockMvc.perform(post("/api/network/requests/" + b.getId()).with(csrf()).with(as(a)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(sent).get("id").asLong();
        mockMvc.perform(post("/api/network/requests/" + id + "/accept").with(csrf()).with(as(b))).andExpect(status().isOk());
    }

    private ResultActions send(User from, User to, String text) throws Exception {
        return mockMvc.perform(post("/api/messages/to/" + to.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":" + objectMapper.writeValueAsString(text) + "}").with(csrf()).with(as(from)));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    @Test
    void onlyConnectedMembersCanMessageEachOther() throws Exception {
        send(ana, ben, "Hi Ben").andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value(containsString("connected")));
        mockMvc.perform(get("/api/messages/conversations/with/" + ben.getId()).with(as(ana))).andExpect(status().isForbidden());

        connect(ana, ben);
        send(ana, ben, "Hi Ben").andExpect(status().isCreated()).andExpect(jsonPath("$.content").value("Hi Ben"))
                .andExpect(jsonPath("$.senderName").value("Ana Messaging"));

        send(ana, cara, "Hi Cara").andExpect(status().isForbidden());
        send(admin, ana, "Hello").andExpect(status().isForbidden());
        mockMvc.perform(post("/api/messages/to/" + ben.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"x\"}").with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void aConversationHoldsBothPeoplesMessagesWithUnreadCountsAndNoEmails() throws Exception {
        connect(ana, ben);
        send(ana, ben, "Hello Ben").andExpect(status().isCreated());
        send(ana, ben, "Are you free to study?").andExpect(status().isCreated());
        send(ben, ana, "Yes, after lunch").andExpect(status().isCreated());

        JsonNode forBen = json(mockMvc.perform(get("/api/messages/conversations").with(as(ben))));
        assertThat(forBen).hasSize(1);
        assertThat(forBen.get(0).get("otherUserName").asText()).isEqualTo("Ana Messaging");
        assertThat(forBen.get(0).get("lastMessageContent").asText()).isEqualTo("Yes, after lunch");
        assertThat(forBen.get(0).get("unreadCount").asLong()).isEqualTo(2);
        long conversation = forBen.get(0).get("id").asLong();

        JsonNode forAna = json(mockMvc.perform(get("/api/messages/conversations").with(as(ana))));
        assertThat(forAna).as("both people share one conversation").hasSize(1);
        assertThat(forAna.get(0).get("id").asLong()).isEqualTo(conversation);
        assertThat(forAna.get(0).get("unreadCount").asLong()).isEqualTo(1);

        String history = mockMvc.perform(get("/api/messages/conversations/" + conversation).with(as(ben)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].content").value("Yes, after lunch"))
                .andExpect(jsonPath("$.content[2].content").value("Hello Ben")).andReturn().getResponse().getContentAsString();
        assertThat(history).doesNotContain("@example.com").doesNotContainIgnoringCase("\"email\"");
        assertThat(forBen.toString()).doesNotContain("@example.com").doesNotContainIgnoringCase("\"email\"");

        mockMvc.perform(post("/api/messages/conversations/" + conversation + "/read").with(csrf()).with(as(ben)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.markedRead").value(2));
        mockMvc.perform(get("/api/messages/conversations").with(as(ben))).andExpect(jsonPath("$[0].unreadCount").value(0));
        mockMvc.perform(get("/api/messages/conversations").with(as(ana))).andExpect(jsonPath("$[0].unreadCount").value(1));
    }

    @Test
    void aConversationIsInvisibleToEveryoneButItsTwoMembers() throws Exception {
        connect(ana, ben);
        send(ana, ben, "Private").andExpect(status().isCreated());
        long conversation = json(mockMvc.perform(get("/api/messages/conversations").with(as(ana)))).get(0).get("id").asLong();

        // Even another connection of theirs gets "not found", the same as for a conversation that doesn't exist.
        connect(cara, ana);
        for (User outsider : List.of(cara, admin)) {
            mockMvc.perform(get("/api/messages/conversations/" + conversation).with(as(outsider))).andExpect(status().isNotFound());
            mockMvc.perform(post("/api/messages/conversations/" + conversation + "/read").with(csrf()).with(as(outsider)))
                    .andExpect(status().isNotFound());
        }
        mockMvc.perform(get("/api/messages/conversations/999999").with(as(ana))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/messages/conversations").with(as(cara))).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void messagesAreCheckedAndTooLongOnesAreRefusedNotCut() throws Exception {
        connect(ana, ben);

        send(ana, ben, "   ").andExpect(status().isBadRequest());
        send(ana, ben, "x".repeat(MessagingService.MAX_LENGTH + 1)).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/messages/to/" + ben.getId()).contentType(MediaType.APPLICATION_JSON).content("{}").with(csrf()).with(as(ana)))
                .andExpect(status().isBadRequest());
        send(ana, ben, "x".repeat(MessagingService.MAX_LENGTH)).andExpect(status().isCreated());
        send(ana, ben, "  padded  ").andExpect(status().isCreated()).andExpect(jsonPath("$.content").value("padded"));
        mockMvc.perform(post("/api/messages/to/999999").contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"hi\"}")
                .with(csrf()).with(as(ana))).andExpect(status().isForbidden());
    }

    @Test
    void endingTheConnectionStopsNewMessagesButKeepsTheHistory() throws Exception {
        connect(ana, ben);
        send(ana, ben, "Before").andExpect(status().isCreated());
        long conversation = json(mockMvc.perform(get("/api/messages/conversations").with(as(ana)))).get(0).get("id").asLong();

        mockMvc.perform(delete("/api/network/connections/" + ben.getId()).with(csrf()).with(as(ana))).andExpect(status().isNoContent());

        send(ana, ben, "After").andExpect(status().isForbidden());
        send(ben, ana, "After").andExpect(status().isForbidden());
        mockMvc.perform(get("/api/messages/conversations/" + conversation).with(as(ben)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void openingAConversationGivesTheSameOneEveryTimeAndStartsItWhenThereIsNone() throws Exception {
        connect(ana, ben);

        long first = json(mockMvc.perform(get("/api/messages/conversations/with/" + ben.getId()).with(as(ana)))).get("id").asLong();
        long again = json(mockMvc.perform(get("/api/messages/conversations/with/" + ben.getId()).with(as(ana)))).get("id").asLong();
        long fromBen = json(mockMvc.perform(get("/api/messages/conversations/with/" + ana.getId()).with(as(ben)))).get("id").asLong();

        assertThat(again).isEqualTo(first);
        assertThat(fromBen).isEqualTo(first);
        mockMvc.perform(get("/api/messages/conversations/with/" + ben.getId()).with(as(ana)))
                .andExpect(jsonPath("$.otherUserName").value("Ben Messaging")).andExpect(jsonPath("$.unreadCount").value(0));
    }
}
