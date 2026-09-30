package com.secureportal.profile;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static com.secureportal.testsupport.TestPrincipals.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Member profiles: details, education/experience/skills, pictures, and who can change what. */
@SpringBootTest(properties = {"storage.provider=local", "storage.local-path=target/profile-test-storage"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*local.*")
class ProfileFlowTest {

    private static final String ME = "profile-flow-me@example.com";
    private static final String OTHER = "profile-flow-other@example.com";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R',
            0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0, 0x1F, 0x15, (byte) 0xC4, (byte) 0x89};

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private User me;
    private User other;

    @BeforeEach
    void users() {
        me = user(ME, "Mia Member");
        other = user(OTHER, "Otto Other");
    }

    @AfterEach
    void cleanUp() {
        for (String email : List.of(ME, OTHER)) {
            userRepository.findByEmailIgnoreCase(email).ifPresent(userRepository::delete);
        }
    }

    @Test
    void aNewMemberHasAnEmptyProfileThatAnyoneSignedInCanSee() throws Exception {
        mockMvc.perform(get("/api/profile").with(as(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mia Member"))
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.headline").doesNotExist())
                .andExpect(jsonPath("$.education.length()").value(0))
                .andExpect(jsonPath("$.certificates.length()").value(0));
        mockMvc.perform(get("/api/profiles/" + me.getId()).with(as(other)))
                .andExpect(jsonPath("$.mine").value(false));
        mockMvc.perform(get("/api/profiles/999999999").with(as(me))).andExpect(status().isNotFound());
    }

    @Test
    void detailsAreSavedTidiedAndValidated() throws Exception {
        mockMvc.perform(put("/api/profile").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"headline\":\"  Student at SJIT  \",\"about\":\"Learning Python.\",\"location\":\"Chennai\",\"website\":\"example.com/me\"}")
                        .with(csrf()).with(as(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline").value("Student at SJIT"))
                .andExpect(jsonPath("$.website").value("https://example.com/me"));

        for (String bad : List.of("{\"website\":\"javascript:alert(1)\"}", "{\"headline\":\"" + "x".repeat(221) + "\"}",
                "{\"about\":\"" + "x".repeat(2601) + "\"}", "{\"website\":\"ftp://files.example.com\"}")) {
            mockMvc.perform(put("/api/profile").contentType(MediaType.APPLICATION_JSON).content(bad).with(csrf()).with(as(me)))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(put("/api/profile").contentType(MediaType.APPLICATION_JSON).content("{}").with(csrf()).with(as(me)))
                .andExpect(jsonPath("$.headline").doesNotExist());
    }

    @Test
    void educationExperienceAndSkillsCanBeAddedEditedAndRemovedOnlyByTheirOwner() throws Exception {
        String school = json(mockMvc.perform(post("/api/profile/entries").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"EDUCATION\",\"title\":\"St. Joseph's Institute of Technology\",\"subtitle\":\"B.Tech CSE\",\"startYear\":2023,\"endYear\":2027}")
                        .with(csrf()).with(as(me)))
                .andExpect(status().isOk()).andReturn()).get("id").asText();
        mockMvc.perform(post("/api/profile/entries").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"EXPERIENCE\",\"title\":\"DailyYou\",\"subtitle\":\"Intern\",\"startYear\":2026,\"description\":\"Built things.\"}")
                        .with(csrf()).with(as(me))).andExpect(status().isOk());
        mockMvc.perform(post("/api/profile/entries").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"SKILL\",\"title\":\"Python\",\"subtitle\":\"ignored\",\"startYear\":1999}")
                        .with(csrf()).with(as(me)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.subtitle").doesNotExist()).andExpect(jsonPath("$.startYear").doesNotExist());

        mockMvc.perform(get("/api/profiles/" + me.getId()).with(as(other)))
                .andExpect(jsonPath("$.education[0].title").value("St. Joseph's Institute of Technology"))
                .andExpect(jsonPath("$.experience[0].title").value("DailyYou"))
                .andExpect(jsonPath("$.skills[0].title").value("Python"));

        for (String bad : List.of("{\"kind\":\"EDUCATION\",\"title\":\"  \"}", "{\"kind\":\"EDUCATION\",\"title\":\"X\",\"startYear\":2024,\"endYear\":2020}",
                "{\"kind\":\"EDUCATION\",\"title\":\"X\",\"startYear\":1800}", "{\"title\":\"No kind\"}")) {
            mockMvc.perform(post("/api/profile/entries").contentType(MediaType.APPLICATION_JSON).content(bad).with(csrf()).with(as(me)))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(put("/api/profile/entries/" + school).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"SJIT\",\"subtitle\":\"B.Tech\",\"startYear\":2023,\"endYear\":2027}").with(csrf()).with(as(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/profile/entries/" + school).with(csrf()).with(as(other))).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/profile/entries/" + school).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"SJIT\",\"subtitle\":\"B.Tech\",\"startYear\":2023,\"endYear\":2027}").with(csrf()).with(as(me)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("SJIT"));
        mockMvc.perform(delete("/api/profile/entries/" + school).with(csrf()).with(as(me))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/profile").with(as(me))).andExpect(jsonPath("$.education.length()").value(0));
    }

    @Test
    void profilePicturesAreValidatedServedAndReplaceTheGooglePictureUntilRemoved() throws Exception {
        MockMultipartFile fake = new MockMultipartFile("file", "me.png", "image/png", "not an image".getBytes());
        mockMvc.perform(multipart("/api/profile/avatar").file(fake).with(csrf()).with(as(me))).andExpect(status().is4xxClientError());

        MockMultipartFile png = new MockMultipartFile("file", "me.png", "image/png", PNG);
        mockMvc.perform(multipart("/api/profile/avatar").file(png).with(csrf()).with(as(me)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hasUploadedAvatar").value(true))
                .andExpect(jsonPath("$.avatarUrl").value(org.hamcrest.Matchers.startsWith("/api/profiles/" + me.getId() + "/avatar")));
        mockMvc.perform(multipart("/api/profile/banner").file(png).with(csrf()).with(as(me)))
                .andExpect(jsonPath("$.bannerUrl").value(org.hamcrest.Matchers.startsWith("/api/profiles/" + me.getId() + "/banner")));

        MvcResult avatar = mockMvc.perform(get("/api/profiles/" + me.getId() + "/avatar").with(as(other))).andExpect(status().isOk()).andReturn();
        assertThat(avatar.getResponse().getContentAsByteArray()).isEqualTo(PNG);
        mockMvc.perform(get("/api/profiles/" + me.getId() + "/banner").with(as(other))).andExpect(status().isOk());

        mockMvc.perform(delete("/api/profile/avatar").with(csrf()).with(as(me))).andExpect(jsonPath("$.hasUploadedAvatar").value(false));
        mockMvc.perform(delete("/api/profile/banner").with(csrf()).with(as(me))).andExpect(jsonPath("$.bannerUrl").doesNotExist());
        mockMvc.perform(get("/api/profiles/" + me.getId() + "/avatar").with(as(other))).andExpect(status().isNotFound());
    }

    @Test
    void profilesRequireSignIn() throws Exception {
        mockMvc.perform(get("/api/profile")).andExpect(status().is3xxRedirection());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private User user(String email, String name) {
        return userRepository.findByEmailIgnoreCase(email).orElseGet(() -> userRepository.save(new User(email, name, null, Role.VIEWER)));
    }
}
