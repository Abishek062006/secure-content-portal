package com.secureportal.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureportal.course.CourseRepository;
import com.secureportal.course.EnrollmentRepository;
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
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import static com.secureportal.testsupport.TestPrincipals.as;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The simulated gateway — the only one testable without live keys — end to end: blocked for a REGISTER
 *  course, blocked for a free course, allowed and enrolling for a paid one, and one order can't be
 *  completed by anyone other than who it belongs to. Real Stripe/Razorpay calls aren't exercised here;
 *  the signature-verification and price-from-server logic around them is reviewed, not integration-tested. */
@SpringBootTest(properties = {"storage.provider=local", "storage.local-path=target/payment-test-storage",
        "app.payment.simulated-enabled=true"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*local.*")
class PaymentFlowTest {

    private static final String ADMIN_EMAIL = "payment-flow-admin@example.com";
    private static final String VIEWER_EMAIL = "payment-flow-viewer@example.com";
    private static final String OTHER_VIEWER_EMAIL = "payment-flow-other@example.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private EnrollmentRepository enrollmentRepository;
    @Autowired
    private PaymentOrderRepository paymentOrderRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private User admin;
    private User viewer;
    private User otherViewer;

    @BeforeEach
    void users() {
        admin = user(ADMIN_EMAIL, Role.ADMIN);
        viewer = user(VIEWER_EMAIL, Role.VIEWER);
        otherViewer = user(OTHER_VIEWER_EMAIL, Role.VIEWER);
    }

    @AfterEach
    void cleanUp() {
        paymentOrderRepository.deleteAll();
        courseRepository.deleteAll();
        userRepository.findByEmailIgnoreCase(ADMIN_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmailIgnoreCase(VIEWER_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmailIgnoreCase(OTHER_VIEWER_EMAIL).ifPresent(userRepository::delete);
    }

    @Test
    void simulatedCheckoutIsRefusedForFreeAndRegisterCoursesButEnrollsForAPaidOne() throws Exception {
        String freeCourse = createAndPublish("Free intro", null, null);
        String registerCourse = createAndPublish("Invite-only lab", null, "REGISTER");
        String paidCourse = createAndPublish("Paid deep dive", 999, null);

        mockMvc.perform(post("/api/payments/create-order").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"" + freeCourse + "\",\"provider\":\"SIMULATED\"}")
                        .with(csrf()).with(as(viewer)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("free")));

        mockMvc.perform(post("/api/payments/create-order").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"" + registerCourse + "\",\"provider\":\"SIMULATED\"}")
                        .with(csrf()).with(as(viewer)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("approval")));

        // Admins buy nothing.
        mockMvc.perform(post("/api/payments/create-order").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"" + paidCourse + "\",\"provider\":\"SIMULATED\"}")
                        .with(csrf()).with(as(admin)))
                .andExpect(status().isForbidden());

        MvcResult created = mockMvc.perform(post("/api/payments/create-order").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"" + paidCourse + "\",\"provider\":\"SIMULATED\"}")
                        .with(csrf()).with(as(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountRupees").value(999))
                .andReturn();
        String orderId = json(created).get("paymentOrderId").asText();

        // Someone else can't complete this order.
        mockMvc.perform(post("/api/payments/simulated/complete").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentOrderId\":\"" + orderId + "\"}")
                        .with(csrf()).with(as(otherViewer)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/payments/simulated/complete").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentOrderId\":\"" + orderId + "\"}")
                        .with(csrf()).with(as(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        boolean enrolled = enrollmentRepository.findByUserId(viewer.getId()).stream()
                .anyMatch(e -> e.getCourseId().toString().equals(paidCourse));
        org.assertj.core.api.Assertions.assertThat(enrolled).isTrue();
    }

    // ---- helpers ----

    private String createAndPublish(String title, Integer priceRupees, String accessType) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/admin/courses");
        request.param("title", title);
        if (priceRupees != null) request.param("priceRupees", String.valueOf(priceRupees));
        if (accessType != null) request.param("accessType", accessType);
        String courseId = json(mockMvc.perform(request.with(csrf()).with(as(admin)))
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
        return courseId;
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private User user(String email, Role role) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> userRepository.save(new User(email, "Payment Flow Test", null, role)));
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
