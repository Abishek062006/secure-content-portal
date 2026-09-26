package com.secureportal.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureportal.ai.LlmClient;
import com.secureportal.analytics.PlatformAnalytics;
import com.secureportal.course.CourseRepository;
import com.secureportal.jobs.JobDispatcher;
import com.secureportal.jobs.RabbitJobDispatcher;
import com.secureportal.user.Role;
import com.secureportal.user.User;
import com.secureportal.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static com.secureportal.testsupport.TestPrincipals.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs against a real Redis and RabbitMQ, so it is skipped unless they are up:
 * {@code docker compose up -d redis rabbitmq} and then {@code BROKERS_UP=true mvn test -Dtest=BrokersIntegrationTest}.
 */
@SpringBootTest(properties = {"storage.provider=local", "storage.local-path=target/brokers-test-storage",
        "app.redis.enabled=true", "app.queue.enabled=true", "ai.model=test-model"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "BROKERS_UP", matches = "true")
@EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*local.*")
class BrokersIntegrationTest {

    private static final String ADMIN = "brokers-admin@example.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private SessionRepository<?> sessionRepository;
    @Autowired
    private RateLimiter rateLimiter;
    @Autowired
    private TtlCache cache;
    @Autowired
    private JobDispatcher dispatcher;
    @MockitoBean
    private LlmClient llmClient;

    @AfterEach
    void cleanUp() {
        courseRepository.deleteAll();
        userRepository.findByEmailIgnoreCase(ADMIN).ifPresent(userRepository::delete);
    }

    @Test
    void sessionsLimitsAndCacheUseRedisAndJobsGoThroughRabbitMq() throws Exception {
        assertThat(sessionRepository.getClass().getSimpleName()).contains("Redis");
        assertThat(rateLimiter).isInstanceOf(RedisRateLimiter.class);
        assertThat(cache).isInstanceOf(RedisTtlCache.class);
        assertThat(dispatcher).isInstanceOf(RabbitJobDispatcher.class);

        String key = "it:" + System.nanoTime();
        assertThat(rateLimiter.hit(key, 2, Duration.ofSeconds(30)).allowed()).isTrue();
        assertThat(rateLimiter.hit(key, 2, Duration.ofSeconds(30)).allowed()).isTrue();
        assertThat(rateLimiter.hit(key, 2, Duration.ofSeconds(30)).allowed()).isFalse();

        // Cached values survive the round trip through Redis's serializer.
        PlatformAnalytics.Funnel funnel = cache.get(key, Duration.ofSeconds(30), () -> new PlatformAnalytics.Funnel(4, 3, 2, 1));
        assertThat(cache.get(key, Duration.ofSeconds(30), () -> new PlatformAnalytics.Funnel(0, 0, 0, 0))).isEqualTo(funnel);

        User admin = userRepository.findByEmailIgnoreCase(ADMIN).orElseGet(() -> userRepository.save(new User(ADMIN, "Admin", null, Role.ADMIN)));
        String course = json(mockMvc.perform(multipart("/api/admin/courses").param("title", "Broker course").with(csrf()).with(as(admin))).andReturn()).get("id").asText();
        String module = json(mockMvc.perform(post("/api/admin/courses/" + course + "/modules").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"M\"}").with(csrf()).with(as(admin))).andReturn()).get("id").asText();
        MockMultipartHttpServletRequestBuilder lesson = multipart("/api/admin/modules/" + module + "/lessons");
        lesson.file(new MockMultipartFile("video", "l.mp4", "video/mp4", new byte[]{0, 0, 0, 0x1C, 'f', 't', 'y', 'p', 'm', 'p', '4', '2',
                0, 0, 0, 0, 'm', 'p', '4', '2', 'i', 's', 'o', 'm', 'a', 'v', 'c', '1'}));
        lesson.file(new MockMultipartFile("transcript", "t.vtt", "text/vtt",
                "WEBVTT\n\n1\n00:00:01.000 --> 00:00:09.000\nHello there.\n".getBytes(StandardCharsets.UTF_8)));
        lesson.param("title", "L");
        String lessonId = json(mockMvc.perform(lesson.with(csrf()).with(as(admin))).andReturn()).get("id").asText();

        when(llmClient.complete(anyString(), anyString())).thenReturn("{\"questions\":[{\"question\":\"Hi?\",\"difficulty\":\"EASY\","
                + "\"options\":[\"A\",\"B\",\"C\",\"D\"],\"correctIndex\":0,\"timestampSeconds\":2,\"explanation\":\"Because.\"}]}");
        String jobId = json(mockMvc.perform(post("/api/admin/lessons/" + lessonId + "/questions/generate").with(csrf()).with(as(admin)))
                .andExpect(status().isAccepted()).andReturn()).get("id").asText();
        JsonNode job = null;
        for (int i = 0; i < 100; i++) {
            job = json(mockMvc.perform(get("/api/admin/generation-jobs/" + jobId).with(as(admin))).andReturn());
            if (List.of("DONE", "FAILED").contains(job.get("status").asText())) {
                break;
            }
            Thread.sleep(200);
        }
        assertThat(job.get("status").asText()).isEqualTo("DONE");
        assertThat(job.get("produced").asInt()).isEqualTo(1);
    }

    private JsonNode json(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
