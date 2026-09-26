package com.secureportal.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureportal.feed.PostRepository;
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
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The limiter is off in other tests; here it is on, through the real interceptor. */
@SpringBootTest(properties = {"storage.provider=local", "storage.local-path=target/ratelimit-test-storage", "app.rate-limit.enabled=true"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*local.*")
class RateLimitFlowTest {

    private static final String A = "ratelimit-a@example.com";
    private static final String B = "ratelimit-b@example.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private User a;
    private User b;

    @BeforeEach
    void users() {
        a = userRepository.findByEmailIgnoreCase(A).orElseGet(() -> userRepository.save(new User(A, "A", null, Role.VIEWER)));
        b = userRepository.findByEmailIgnoreCase(B).orElseGet(() -> userRepository.save(new User(B, "B", null, Role.VIEWER)));
    }

    @AfterEach
    void cleanUp() {
        postRepository.deleteAll();
        for (String email : List.of(A, B)) {
            userRepository.findByEmailIgnoreCase(email).ifPresent(userRepository::delete);
        }
    }

    @Test
    void oneUserHittingAnEndpointTooOftenGetsA429WithARetryTimeAndOthersAreUnaffected() throws Exception {
        String postId = objectMapper.readTree(mockMvc.perform(multipart("/api/posts").param("body", "Hello").with(csrf()).with(as(b)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asText();

        for (int i = 0; i < 20; i++) {
            mockMvc.perform(post("/api/posts/" + postId + "/comments").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"body\":\"Comment " + i + "\"}").with(csrf()).with(as(a))).andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/posts/" + postId + "/comments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"One too many\"}").with(csrf()).with(as(a)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error").value(containsString("too often")));

        mockMvc.perform(post("/api/posts/" + postId + "/comments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Someone else is fine\"}").with(csrf()).with(as(b))).andExpect(status().isOk());
    }
}
