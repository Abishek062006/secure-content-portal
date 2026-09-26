package com.secureportal.course;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureportal.user.Role;
import com.secureportal.user.User;
import com.secureportal.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
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
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.secureportal.testsupport.TestPrincipals.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Module study material: adding it, viewing it protected, and downloading only when the admin allows. */
@SpringBootTest(properties = {"storage.provider=local", "storage.local-path=target/material-test-storage"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*local.*")
class MaterialFlowTest {

    private static final String ADMIN = "material-flow-admin@example.com";
    private static final String LEARNER = "material-flow-learner@example.com";
    private static final byte[] MP4 = {0x00, 0x00, 0x00, 0x1C, 'f', 't', 'y', 'p', 'm', 'p', '4', '2',
            0x00, 0x00, 0x00, 0x00, 'm', 'p', '4', '2', 'i', 's', 'o', 'm', 'a', 'v', 'c', '1'};

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private CourseMaterialRepository materialRepository;
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
        courseRepository.deleteAll();
        for (String email : List.of(ADMIN, LEARNER)) {
            userRepository.findByEmailIgnoreCase(email).ifPresent(userRepository::delete);
        }
    }

    @Test
    void adminAddsEveryKindOfMaterialWithValidationAndTheDownloadChoice() throws Exception {
        String module = fixture().module;

        JsonNode pdf = json(add(module, "Slides", "slides.pdf", "application/pdf", pdfBytes(3), false).andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("PDF")).andExpect(jsonPath("$.pageCount").value(3))
                .andExpect(jsonPath("$.downloadable").value(false)).andReturn());
        add(module, "Slides copy", "slides2.pdf", "application/pdf", pdfBytes(1), true)
                .andExpect(status().isOk()).andExpect(jsonPath("$.downloadable").value(true));
        add(module, "Reading", "notes.html", "text/html", "<h1>Hi</h1><script>alert(1)</script>".getBytes(), false)
                .andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("HTML"));
        add(module, "Demo", "demo.mp4", "video/mp4", MP4, false)
                .andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("VIDEO"));
        add(module, "Worksheet", "sheet.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "PK data".getBytes(), false)
                .andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("DOCUMENT"))
                .andExpect(jsonPath("$.downloadable").value(true));
        MockMultipartHttpServletRequestBuilder link = multipart("/api/admin/modules/" + module + "/materials");
        link.param("title", "Python docs").param("url", "docs.python.org/3/").param("downloadable", "true");
        mockMvc.perform(link.with(csrf()).with(as(admin))).andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("LINK")).andExpect(jsonPath("$.url").value("https://docs.python.org/3/"))
                .andExpect(jsonPath("$.downloadable").value(false));

        add(module, "Bad", "tool.exe", "application/octet-stream", "MZ".getBytes(), false).andExpect(status().isBadRequest());
        add(module, "Fake pdf", "fake.pdf", "application/pdf", "not a pdf".getBytes(), false).andExpect(status().isBadRequest());
        add(module, " ", "slides.pdf", "application/pdf", pdfBytes(1), false).andExpect(status().isBadRequest());
        for (String bad : List.of("javascript:alert(1)", "ftp://files.example.com", "mailto:a@b.c")) {
            MockMultipartHttpServletRequestBuilder request = multipart("/api/admin/modules/" + module + "/materials");
            request.param("title", "Bad link").param("url", bad);
            mockMvc.perform(request.with(csrf()).with(as(admin))).andExpect(status().isBadRequest());
        }
        mockMvc.perform(multipart("/api/admin/modules/" + module + "/materials").param("title", "Nothing").with(csrf()).with(as(admin)))
                .andExpect(status().isBadRequest());
        add(module, "Slides", "slides.pdf", "application/pdf", pdfBytes(1), false).andReturn();
        assertThat(materialRepository.count()).isEqualTo(7);

        // Viewers can't manage material.
        MockMultipartHttpServletRequestBuilder byViewer = multipart("/api/admin/modules/" + module + "/materials");
        byViewer.file(new MockMultipartFile("file", "a.pdf", "application/pdf", pdfBytes(1))).param("title", "x");
        mockMvc.perform(byViewer.with(csrf()).with(as(learner))).andExpect(status().isForbidden());

        // Editing: a PDF can flip to downloadable; a document stays downloadable whatever is sent.
        mockMvc.perform(put("/api/admin/materials/" + pdf.get("id").asText()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Lecture slides\",\"downloadable\":true}").with(csrf()).with(as(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Lecture slides")).andExpect(jsonPath("$.downloadable").value(true));
        mockMvc.perform(delete("/api/admin/materials/" + pdf.get("id").asText()).with(csrf()).with(as(admin))).andExpect(status().isNoContent());
        assertThat(materialRepository.count()).isEqualTo(6);
    }

    @Test
    void learnersOpenMaterialThroughProtectedViewersAndDownloadOnlyWhatIsAllowed() throws Exception {
        Fixture f = fixture();
        JsonNode locked = json(add(f.module, "View only", "slides.pdf", "application/pdf", pdfBytes(2), false).andReturn());
        JsonNode open = json(add(f.module, "Downloadable", "handout.pdf", "application/pdf", pdfBytes(1), true).andReturn());
        JsonNode html = json(add(f.module, "Reading", "notes.html", "text/html", "<h1>Hi</h1><script>alert(1)</script>".getBytes(), true).andReturn());
        JsonNode video = json(add(f.module, "Demo", "demo.mp4", "video/mp4", MP4, false).andReturn());
        JsonNode doc = json(add(f.module, "Worksheet", "sheet.docx", "x/y", "PK data".getBytes(), false).andReturn());
        MockMultipartHttpServletRequestBuilder link = multipart("/api/admin/modules/" + f.module + "/materials");
        link.param("title", "Docs").param("url", "https://example.com");
        JsonNode linkNode = json(mockMvc.perform(link.with(csrf()).with(as(admin))).andReturn());

        String base = "/api/courses/" + f.course + "/materials/";
        mockMvc.perform(get(base + locked.get("id").asText()).with(as(learner))).andExpect(status().isForbidden());
        mockMvc.perform(get(base + open.get("id").asText() + "/download").with(as(learner))).andExpect(status().isForbidden());

        mockMvc.perform(post("/api/courses/" + f.course + "/enroll").with(csrf()).with(as(learner))).andExpect(status().isOk());
        mockMvc.perform(get("/api/courses/" + f.course).with(as(learner)))
                .andExpect(jsonPath("$.modules[0].materials.length()").value(6))
                .andExpect(jsonPath("$.modules[0].materials[0].title").value("View only"));

        // PDF: watermarked page images through a session-bound ticket; no download unless allowed.
        MvcResult opened = mockMvc.perform(get(base + locked.get("id").asText()).with(as(learner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ticket").exists()).andExpect(jsonPath("$.material.pageCount").value(2)).andReturn();
        String ticket = json(opened).get("ticket").asText();
        Cookie session = opened.getResponse().getCookie("SESSION");
        assertThat(session).isNotNull();
        MvcResult page = mockMvc.perform(get("/api/material/" + ticket + "/pdf/1").cookie(session).with(as(learner)))
                .andExpect(status().isOk()).andReturn();
        assertThat(page.getResponse().getContentType()).isEqualTo("image/jpeg");
        mockMvc.perform(get("/api/material/" + ticket + "/pdf/3").cookie(session).with(as(learner))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/material/" + ticket + "/pdf/1").with(as(learner))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/material/" + ticket + "/html").cookie(session).with(as(learner))).andExpect(status().isNotFound());
        mockMvc.perform(get(base + locked.get("id").asText() + "/download").with(as(learner)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value(containsString("can't be downloaded")));

        // Downloadable PDF and document come as attachments.
        MvcResult pdfDownload = mockMvc.perform(get(base + open.get("id").asText() + "/download").with(as(learner)))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", containsString("attachment"))).andReturn();
        assertThat(new String(pdfDownload.getResponse().getContentAsByteArray(), 0, 5)).isEqualTo("%PDF-");
        mockMvc.perform(get(base + doc.get("id").asText() + "/download").with(as(learner)))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", "application/octet-stream"))
                .andExpect(content().bytes("PK data".getBytes()));

        // HTML: sanitized, sandboxed.
        MvcResult htmlOpen = mockMvc.perform(get(base + html.get("id").asText()).with(as(learner))).andReturn();
        MvcResult htmlBody = mockMvc.perform(get("/api/material/" + json(htmlOpen).get("ticket").asText() + "/html")
                        .cookie(htmlOpen.getResponse().getCookie("SESSION")).with(as(learner)))
                .andExpect(status().isOk()).andExpect(header().string("Content-Security-Policy", containsString("sandbox"))).andReturn();
        assertThat(htmlBody.getResponse().getContentAsString()).contains("<h1>Hi</h1>").doesNotContain("script");

        // Video streams in ranges but isn't downloadable.
        MvcResult videoOpen = mockMvc.perform(get(base + video.get("id").asText()).with(as(learner))).andReturn();
        mockMvc.perform(get("/api/material/" + json(videoOpen).get("ticket").asText() + "/video")
                        .cookie(videoOpen.getResponse().getCookie("SESSION")).with(as(learner)).header("Range", "bytes=0-3"))
                .andExpect(status().isPartialContent());
        mockMvc.perform(get(base + video.get("id").asText() + "/download").with(as(learner))).andExpect(status().isForbidden());

        // Links carry their address and no ticket; documents no ticket either.
        mockMvc.perform(get(base + linkNode.get("id").asText()).with(as(learner)))
                .andExpect(jsonPath("$.material.url").value("https://example.com")).andExpect(jsonPath("$.ticket").doesNotExist());
        mockMvc.perform(get(base + doc.get("id").asText()).with(as(learner))).andExpect(jsonPath("$.ticket").doesNotExist());

        // Another course's id can't be used to reach this material.
        mockMvc.perform(get("/api/courses/" + java.util.UUID.randomUUID() + "/materials/" + open.get("id").asText()).with(as(learner)))
                .andExpect(status().isNotFound());

        // Deleting the module takes its material with it.
        mockMvc.perform(delete("/api/admin/modules/" + f.module).with(csrf()).with(as(admin))).andExpect(status().isNoContent());
        assertThat(materialRepository.count()).isZero();
    }

    // ---- helpers ----

    private record Fixture(String course, String module) {
    }

    private Fixture fixture() throws Exception {
        String course = json(mockMvc.perform(multipart("/api/admin/courses").param("title", "Material course").with(csrf()).with(as(admin)))
                .andExpect(status().isOk()).andReturn()).get("id").asText();
        String module = json(mockMvc.perform(post("/api/admin/courses/" + course + "/modules").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"M\"}").with(csrf()).with(as(admin))).andReturn()).get("id").asText();
        MockMultipartHttpServletRequestBuilder lesson = multipart("/api/admin/modules/" + module + "/lessons");
        lesson.file(new MockMultipartFile("video", "lecture.mp4", "video/mp4", MP4)).param("title", "L");
        mockMvc.perform(lesson.with(csrf()).with(as(admin))).andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/courses/" + course + "/publish").with(csrf()).with(as(admin))).andExpect(status().isOk());
        return new Fixture(course, module);
    }

    private org.springframework.test.web.servlet.ResultActions add(String module, String title, String filename, String type,
                                                                   byte[] bytes, boolean downloadable) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/admin/modules/" + module + "/materials");
        request.file(new MockMultipartFile("file", filename, type, bytes));
        request.param("title", title).param("downloadable", String.valueOf(downloadable));
        return mockMvc.perform(request.with(csrf()).with(as(admin)));
    }

    private static byte[] pdfBytes(int pages) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                doc.addPage(new PDPage());
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private User user(String email, Role role) {
        return userRepository.findByEmailIgnoreCase(email).orElseGet(() -> userRepository.save(new User(email, "Material Test", null, role)));
    }
}
