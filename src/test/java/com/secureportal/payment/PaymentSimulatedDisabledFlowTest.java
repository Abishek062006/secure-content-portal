package com.secureportal.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureportal.course.CourseRepository;
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
import org.springframework.test.web.servlet.MvcResult;

import static com.secureportal.testsupport.TestPrincipals.as;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Confirms the actual fix: with the flag off (the real default everywhere except a local dev run — see
 *  PaymentProperties), the simulated gateway isn't offered and can't be used even if someone hits the
 *  endpoint directly. This is a separate Spring context (its own {@code @SpringBootTest properties}) purely
 *  to pin that one property differently from {@link PaymentFlowTest}. */
@SpringBootTest(properties = {"storage.provider=local", "storage.local-path=target/payment-disabled-test-storage",
        "app.payment.simulated-enabled=false"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*local.*")
class PaymentSimulatedDisabledFlowTest {

    private static final String ADMIN_EMAIL = "payment-disabled-admin@example.com";
    private static final String VIEWER_EMAIL = "payment-disabled-viewer@example.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private User admin;
    private User viewer;

    @BeforeEach
    void users() {
        admin = user(ADMIN_EMAIL, Role.ADMIN);
        viewer = user(VIEWER_EMAIL, Role.VIEWER);
    }

    @AfterEach
    void cleanUp() {
        courseRepository.deleteAll();
        userRepository.findByEmailIgnoreCase(ADMIN_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmailIgnoreCase(VIEWER_EMAIL).ifPresent(userRepository::delete);
    }

    @Test
    void simulatedProviderIsAbsentFromConfigAndRefusedIfCalledDirectly() throws Exception {
        mockMvc.perform(get("/api/payments/config").with(as(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulatedEnabled").value(false))
                .andExpect(jsonPath("$.availableProviders", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("SIMULATED"))));

        String courseId = json(mockMvc.perform(multipart("/api/admin/courses").param("title", "Paid course")
                        .param("priceRupees", "499").with(csrf()).with(as(admin)))
                .andExpect(status().isOk()).andReturn()).get("id").asText();
        String module = json(mockMvc.perform(post("/api/admin/courses/" + courseId + "/modules")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"M\"}")
                        .with(csrf()).with(as(admin))).andReturn()).get("id").asText();
        mockMvc.perform(multipart("/api/admin/modules/" + module + "/lessons")
                        .file(new org.springframework.mock.web.MockMultipartFile("video", "l.mp4", "video/mp4", mp4()))
                        .param("title", "L").with(csrf()).with(as(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/courses/" + courseId + "/publish").with(csrf()).with(as(admin)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/payments/create-order").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"" + courseId + "\",\"provider\":\"SIMULATED\"}")
                        .with(csrf()).with(as(viewer)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("simulated")));
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private User user(String email, Role role) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> userRepository.save(new User(email, "Payment Disabled Test", null, role)));
    }

    private static byte[] mp4() {
        byte[] head = {0x00, 0x00, 0x00, 0x1C, 'f', 't', 'y', 'p', 'm', 'p', '4', '2',
                0x00, 0x00, 0x00, 0x00, 'm', 'p', '4', '2', 'i', 's', 'o', 'm', 'a', 'v', 'c', '1'};
        byte[] bytes = new byte[4096];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i % 251);
        }
        System.arraycopy(head, 0, bytes, 0, head.length);
        return bytes;
    }
}
