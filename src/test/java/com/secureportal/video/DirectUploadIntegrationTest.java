package com.secureportal.video;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureportal.course.CourseRepository;
import com.secureportal.course.HlsStatus;
import com.secureportal.course.LessonRepository;
import com.secureportal.storage.StorageService;
import com.secureportal.user.Role;
import com.secureportal.user.User;
import com.secureportal.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static com.secureportal.testsupport.TestPrincipals.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole video path against a real S3-compatible server and a real ffmpeg (through Docker): the browser's side of a
 * multipart upload, server-side checks, HLS packaging, and ticketed playback. Skipped unless the services are up:
 * {@code docker compose up -d s3}, then {@code S3_UP=true mvn test -Dtest=DirectUploadIntegrationTest}.
 * It needs {@code /private/tmp/gn/sample.mp4} (see the README) or makes one with the ffmpeg wrapper.
 */
@SpringBootTest(properties = {"storage.provider=s3", "storage.endpoint=http://localhost:8333", "storage.bucket=content-test",
        "storage.access-key=minioadmin", "storage.secret-key=minioadmin", "storage.create-bucket=true", "transcode.enabled=true"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "S3_UP", matches = "true")
@EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*local.*")
class DirectUploadIntegrationTest {

    private static final String ADMIN = "direct-upload-admin@example.com";
    private static final String LEARNER = "direct-upload-learner@example.com";

    @DynamicPropertySource
    static void tools(DynamicPropertyRegistry registry) {
        String dir = Path.of("scripts").toAbsolutePath().toString();
        registry.add("transcode.ffmpeg", () -> dir + "/ffmpeg-docker.sh");
        registry.add("transcode.ffprobe", () -> dir + "/ffprobe-docker.sh");
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private LessonRepository lessonRepository;
    @Autowired
    private StorageService storage;
    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient http = HttpClient.newHttpClient();

    @AfterEach
    void cleanUp() {
        courseRepository.deleteAll();
        for (String email : List.of(ADMIN, LEARNER)) {
            userRepository.findByEmailIgnoreCase(email).ifPresent(userRepository::delete);
        }
    }

    @Test
    void aVideoUploadedInPartsBecomesALessonThenAdaptiveStreamingThatPlaysBehindATicket() throws Exception {
        User admin = userRepository.save(new User(ADMIN, "Admin", null, Role.ADMIN));
        User learner = userRepository.save(new User(LEARNER, "Learner", null, Role.VIEWER));
        byte[] video = sampleVideo();
        String course = json(mockMvc.perform(multipart("/api/admin/courses").param("title", "Direct course").with(csrf()).with(as(admin))).andReturn()).get("id").asText();
        String module = json(mockMvc.perform(post("/api/admin/courses/" + course + "/modules").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"M\"}").with(csrf()).with(as(admin))).andReturn()).get("id").asText();

        mockMvc.perform(get("/api/admin/uploads/config").with(as(admin)))
                .andExpect(jsonPath("$.direct").value(true)).andExpect(jsonPath("$.partSizeBytes").value(DirectUploadService.PART_SIZE));
        mockMvc.perform(get("/api/admin/uploads/config").with(as(learner))).andExpect(status().isForbidden());

        // Rejections happen before any bytes move.
        start(admin, module, "bad.exe", 100).andExpect(status().isBadRequest());
        start(admin, module, "empty.mp4", 0).andExpect(status().isBadRequest());

        JsonNode started = json(start(admin, module, "sample.mp4", video.length).andExpect(status().isOk()).andReturn());
        String uploadId = started.get("id").asText();
        assertThat(started.get("partCount").asInt()).isEqualTo(1);

        String url = json(mockMvc.perform(post("/api/admin/uploads/" + uploadId + "/parts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"partNumbers\":[1]}").with(csrf()).with(as(admin))).andExpect(status().isOk()).andReturn()).get(0).get("url").asText();
        mockMvc.perform(post("/api/admin/uploads/" + uploadId + "/parts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"partNumbers\":[2]}").with(csrf()).with(as(admin))).andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/admin/uploads/" + uploadId + "/complete").contentType(MediaType.APPLICATION_JSON)
                .content("{\"parts\":[]}").with(csrf()).with(as(admin))).andExpect(status().isBadRequest());

        String etag = put(url, video);
        mockMvc.perform(get("/api/admin/uploads/" + uploadId + "/parts").with(as(admin)))
                .andExpect(jsonPath("$[0].partNumber").value(1)).andExpect(jsonPath("$[0].etag").value(etag));

        JsonNode lesson = json(mockMvc.perform(post("/api/admin/uploads/" + uploadId + "/complete").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parts\":[{\"partNumber\":1,\"etag\":" + objectMapper.writeValueAsString(etag) + "}]}").with(csrf()).with(as(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Uploaded lesson")).andReturn());
        String lessonId = lesson.get("id").asText();
        assertThat(lesson.get("hlsStatus").asText()).isEqualTo("PROCESSING");
        mockMvc.perform(post("/api/admin/uploads/" + uploadId + "/complete").contentType(MediaType.APPLICATION_JSON)
                .content("{\"parts\":[]}").with(csrf()).with(as(admin))).andExpect(status().isBadRequest());

        // The original plays straight away; HLS turns up once ffmpeg has finished.
        awaitHls(UUID.fromString(lessonId));
        assertThat(lessonRepository.findById(UUID.fromString(lessonId)).orElseThrow().getHlsStatus()).isEqualTo(HlsStatus.READY);

        mockMvc.perform(post("/api/admin/courses/" + course + "/publish").with(csrf()).with(as(admin))).andExpect(status().isOk());
        mockMvc.perform(post("/api/courses/" + course + "/enroll").with(csrf()).with(as(learner))).andExpect(status().isOk());
        MvcResult opened = mockMvc.perform(get("/api/courses/" + course + "/lessons/" + lessonId).with(as(learner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hlsReady").value(true)).andReturn();
        String ticket = json(opened).get("ticket").asText();
        Cookie session = opened.getResponse().getCookie("SESSION");
        String base = "/api/course-stream/" + ticket + "/hls/";

        MvcResult master = mockMvc.perform(get(base + "master.m3u8").cookie(session).with(as(learner))).andExpect(status().isOk()).andReturn();
        assertThat(master.getResponse().getContentType()).isEqualTo("application/vnd.apple.mpegurl");
        String masterText = master.getResponse().getContentAsString();
        assertThat(masterText).startsWith("#EXTM3U").contains("360p/index.m3u8").contains("720p/index.m3u8").doesNotContain("1080p");

        String playlist = mockMvc.perform(get(base + "720p/index.m3u8").cookie(session).with(as(learner))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String firstSegment = Arrays.stream(playlist.split("\n")).filter(l -> l.endsWith(".ts")).findFirst().orElseThrow();
        MvcResult segment = mockMvc.perform(get(base + "720p/" + firstSegment).cookie(session).with(as(learner)).header("Range", "bytes=0-187"))
                .andExpect(status().isPartialContent()).andReturn();
        assertThat(segment.getResponse().getContentAsByteArray()).hasSize(188);
        assertThat(segment.getResponse().getContentAsByteArray()[0]).as("MPEG-TS sync byte").isEqualTo((byte) 0x47);

        // Only the files this app writes, only with the right ticket.
        mockMvc.perform(get(base + "../video").cookie(session).with(as(learner))).andExpect(status().is4xxClientError());
        mockMvc.perform(get(base + "720p/evil.txt").cookie(session).with(as(learner))).andExpect(status().isNotFound());
        mockMvc.perform(get(base + "master.m3u8").with(as(learner))).andExpect(status().isForbidden());

        // Deleting the lesson removes the packaged files too.
        assertThat(storage.exists("hls/" + lessonId + "/master.m3u8")).isTrue();
        mockMvc.perform(delete("/api/admin/lessons/" + lessonId).with(csrf()).with(as(admin))).andExpect(status().isNoContent());
        assertThat(storage.exists("hls/" + lessonId + "/master.m3u8")).isFalse();
    }

    @Test
    void aFileThatIsNotAVideoIsRejectedAndRemovedAndSeveralPartsAreAssembled() throws Exception {
        User admin = userRepository.save(new User(ADMIN, "Admin", null, Role.ADMIN));
        String course = json(mockMvc.perform(multipart("/api/admin/courses").param("title", "Reject course").with(csrf()).with(as(admin))).andReturn()).get("id").asText();
        String module = json(mockMvc.perform(post("/api/admin/courses/" + course + "/modules").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"M\"}").with(csrf()).with(as(admin))).andReturn()).get("id").asText();

        // Text pretending to be an mp4.
        byte[] fake = "this is not a video".repeat(20).getBytes(StandardCharsets.UTF_8);
        JsonNode started = json(start(admin, module, "fake.mp4", fake.length).andReturn());
        String url = presign(admin, started.get("id").asText(), 1);
        String etag = put(url, fake);
        mockMvc.perform(post("/api/admin/uploads/" + started.get("id").asText() + "/complete").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parts\":[{\"partNumber\":1,\"etag\":" + objectMapper.writeValueAsString(etag) + "}]}").with(csrf()).with(as(admin)))
                .andExpect(status().isBadRequest());
        assertThat(lessonRepository.count()).as("no lesson was made from a rejected file").isZero();

        // A real video padded past one part, sent as two parts.
        byte[] real = sampleVideo();
        byte[] big = new byte[(int) DirectUploadService.PART_SIZE + 1024];
        System.arraycopy(real, 0, big, 0, real.length);
        JsonNode multi = json(start(admin, module, "big.mp4", big.length).andReturn());
        assertThat(multi.get("partCount").asInt()).isEqualTo(2);
        String id = multi.get("id").asText();
        List<String> parts = new ArrayList<>();
        String part1 = put(presign(admin, id, 1), Arrays.copyOfRange(big, 0, (int) DirectUploadService.PART_SIZE));
        parts.add("{\"partNumber\":1,\"etag\":" + objectMapper.writeValueAsString(part1) + "}");
        // Resume: the bucket reports part 1 as already there, so only part 2 is still to send.
        mockMvc.perform(get("/api/admin/uploads/" + id + "/parts").with(as(admin))).andExpect(jsonPath("$.length()").value(1));
        String part2 = put(presign(admin, id, 2), Arrays.copyOfRange(big, (int) DirectUploadService.PART_SIZE, big.length));
        parts.add("{\"partNumber\":2,\"etag\":" + objectMapper.writeValueAsString(part2) + "}");
        mockMvc.perform(post("/api/admin/uploads/" + id + "/complete").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parts\":[" + String.join(",", parts) + "]}").with(csrf()).with(as(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.videoSizeLabel").exists());
        assertThat(lessonRepository.count()).isEqualTo(1);

        JsonNode aborted = json(start(admin, module, "abandon.mp4", 10).andReturn());
        mockMvc.perform(delete("/api/admin/uploads/" + aborted.get("id").asText()).with(csrf()).with(as(admin))).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/admin/uploads/" + aborted.get("id").asText() + "/parts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"partNumbers\":[1]}").with(csrf()).with(as(admin))).andExpect(status().isBadRequest());
    }

    // ---- helpers ----

    private org.springframework.test.web.servlet.ResultActions start(User admin, String module, String filename, long size) throws Exception {
        return mockMvc.perform(post("/api/admin/modules/" + module + "/uploads").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Uploaded lesson\",\"filename\":\"" + filename + "\",\"sizeBytes\":" + size + "}").with(csrf()).with(as(admin)));
    }

    private String presign(User admin, String uploadId, int part) throws Exception {
        return json(mockMvc.perform(post("/api/admin/uploads/" + uploadId + "/parts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"partNumbers\":[" + part + "]}").with(csrf()).with(as(admin))).andExpect(status().isOk()).andReturn()).get(0).get("url").asText();
    }

    /** What the browser does: PUT the bytes to the signed URL and keep the ETag it answers with. */
    private String put(String url, byte[] bytes) throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url)).PUT(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return response.headers().firstValue("ETag").orElseThrow();
    }

    private void awaitHls(UUID lessonId) throws Exception {
        for (int i = 0; i < 360; i++) {
            HlsStatus status = lessonRepository.findById(lessonId).orElseThrow().getHlsStatus();
            if (status == HlsStatus.READY) {
                return;
            }
            if (status == HlsStatus.FAILED) {
                throw new AssertionError("Transcoding failed: " + lessonRepository.findById(lessonId).orElseThrow().getHlsMessage());
            }
            Thread.sleep(500);
        }
        throw new AssertionError("Transcoding did not finish in time");
    }

    private byte[] sampleVideo() throws Exception {
        Path sample = Path.of("/private/tmp/gn/sample.mp4");
        if (!Files.exists(sample)) {
            Files.createDirectories(sample.getParent());
            new ProcessBuilder(Path.of("scripts/ffmpeg-docker.sh").toAbsolutePath().toString(), "-y", "-loglevel", "error", "-f", "lavfi", "-i",
                    "testsrc=duration=8:size=1280x720:rate=25", "-f", "lavfi", "-i", "sine=frequency=440:duration=8", "-pix_fmt", "yuv420p",
                    "-c:v", "libx264", "-c:a", "aac", "-shortest", sample.toString()).inheritIO().start().waitFor();
        }
        return Files.readAllBytes(sample);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
