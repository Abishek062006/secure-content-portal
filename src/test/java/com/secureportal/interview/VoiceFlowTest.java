package com.secureportal.interview;

import com.secureportal.ai.AiException;
import com.secureportal.ai.AiNotConfiguredException;
import com.secureportal.ai.SpeechToText;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.secureportal.testsupport.TestPrincipals.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spoken answers: only real recordings are sent on, consent is required, the daily cap holds, a failed transcription costs
 * nothing, and the transcript comes back with delivery notes and is never stored.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*local.*")
class VoiceFlowTest {

    private static final String ADMIN = "voice-admin@example.com";
    private static final String ONE = "voice-one@example.com";
    private static final String TWO = "voice-two@example.com";
    /** The first bytes of a WebM recording, which is all the type check looks at. */
    private static final byte[] WEBM = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 1, 2, 3, 4, 5, 6, 7, 8};

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MockInterviewSessionRepository sessionRepository;
    @MockitoBean
    private SpeechToText speech;

    private User admin;
    private User one;
    private User two;

    @BeforeEach
    void users() {
        admin = user(ADMIN, Role.ADMIN);
        one = user(ONE, Role.VIEWER);
        two = user(TWO, Role.VIEWER);
        doReturn("Um, I would use a cache, you know, and basically measure first. Then I would index the table.")
                .when(speech).transcribe(any(), anyString(), anyString(), any());
    }

    @AfterEach
    void cleanUp() {
        for (String email : List.of(ADMIN, ONE, TWO)) {
            userRepository.findByEmailIgnoreCase(email).ifPresent(userRepository::delete);
        }
    }

    private User user(String email, Role role) {
        return userRepository.findByEmailIgnoreCase(email).orElseGet(() -> userRepository.save(new User(email, email, null, role)));
    }

    private org.springframework.test.web.servlet.ResultActions send(User who, byte[] audio, boolean consent, int seconds) throws Exception {
        return mockMvc.perform(multipart("/api/interviews/transcribe").file(new MockMultipartFile("audio", "a.webm", "audio/webm", audio))
                .param("consent", String.valueOf(consent)).param("durationSeconds", String.valueOf(seconds)).with(csrf()).with(as(who)));
    }

    @Test
    void aRecordingComesBackAsATranscriptWithDeliveryNotes() throws Exception {
        send(one, WEBM, true, 12).andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.containsString("I would use a cache")))
                .andExpect(jsonPath("$.words").value(18)).andExpect(jsonPath("$.fillerWords").value(3))
                .andExpect(jsonPath("$.wordsPerMinute").value(90));
        // With no usable duration the pace is left out rather than guessed.
        send(one, WEBM, true, 0).andExpect(status().isOk()).andExpect(jsonPath("$.wordsPerMinute").doesNotExist());
    }

    @Test
    void theSpeechModelIsToldTheRoleAndSkillsOfTheLearnersOwnInterviewOnly() throws Exception {
        MockInterviewSession mine = sessionRepository.save(new MockInterviewSession(one.getId(), InterviewTrack.STUDENT, InterviewType.TECHNICAL,
                InterviewDifficulty.MEDIUM, new MockInterviewSession.Goal(InterviewSource.SKILLS, "Backend developer", "Java, Redis", null, null), 5));
        MockInterviewSession theirs = sessionRepository.save(new MockInterviewSession(two.getId(), InterviewTrack.STUDENT, InterviewType.TECHNICAL,
                InterviewDifficulty.MEDIUM, new MockInterviewSession.Goal(InterviewSource.SKILLS, "Secret role", "Secret skill", null, null), 5));

        mockMvc.perform(multipart("/api/interviews/transcribe").file(new MockMultipartFile("audio", "a.webm", "audio/webm", WEBM))
                .param("consent", "true").param("sessionId", String.valueOf(mine.getId())).with(csrf()).with(as(one))).andExpect(status().isOk());
        mockMvc.perform(multipart("/api/interviews/transcribe").file(new MockMultipartFile("audio", "a.webm", "audio/webm", WEBM))
                .param("consent", "true").param("sessionId", String.valueOf(theirs.getId())).with(csrf()).with(as(one))).andExpect(status().isOk());
        send(one, WEBM, true, 10).andExpect(status().isOk());

        org.mockito.ArgumentCaptor<String> hint = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(speech, org.mockito.Mockito.times(3)).transcribe(any(), anyString(), anyString(), hint.capture());
        assertThat(hint.getAllValues().get(0)).contains("Backend developer").contains("Java, Redis"); // their own interview
        assertThat(hint.getAllValues().get(1)).isNull();                                                // someone else's: nothing leaks
        assertThat(hint.getAllValues().get(2)).isNull();                                                // no interview named
    }

    @Test
    void onlyConsentedLearnerRecordingsOfARealAudioTypeAreSentOn() throws Exception {
        send(one, WEBM, false, 10).andExpect(status().isBadRequest());
        send(one, "this is not audio at all, just text".getBytes(), true, 10).andExpect(status().isBadRequest());
        send(one, new byte[0], true, 10).andExpect(status().isBadRequest());
        byte[] huge = new byte[4 * 1024 * 1024 + 1];
        System.arraycopy(WEBM, 0, huge, 0, WEBM.length);
        send(one, huge, true, 10).andExpect(status().isBadRequest());
        send(admin, WEBM, true, 10).andExpect(status().isForbidden());
        verify(speech, never()).transcribe(any(), anyString(), anyString(), any());
    }

    @Test
    void aFailedOrSilentTranscriptionCostsNothingAndProblemsAreReportedHonestly() throws Exception {
        doThrow(new AiException("The speech service answered HTTP 500.")).when(speech).transcribe(any(), anyString(), anyString(), any());
        send(one, WEBM, true, 10).andExpect(status().isBadGateway());
        doThrow(new AiNotConfiguredException()).when(speech).transcribe(any(), anyString(), anyString(), any());
        send(one, WEBM, true, 10).andExpect(status().isServiceUnavailable());
        doReturn("   ").when(speech).transcribe(any(), anyString(), anyString(), any());
        send(one, WEBM, true, 10).andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/interviews/quota").with(as(one))).andExpect(jsonPath("$.voiceUsed").value(0));
    }

    @Test
    void voiceAnswersHaveADailyCapPerLearner() throws Exception {
        mockMvc.perform(get("/api/interviews/quota").with(as(one)))
                .andExpect(jsonPath("$.voiceUsed").value(0)).andExpect(jsonPath("$.voiceLimit").value(VoiceService.MAX_PER_DAY));
        for (int i = 0; i < VoiceService.MAX_PER_DAY; i++) {
            send(one, WEBM, true, 10).andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/interviews/quota").with(as(one))).andExpect(jsonPath("$.voiceUsed").value(VoiceService.MAX_PER_DAY));
        send(one, WEBM, true, 10).andExpect(status().isTooManyRequests());
        // The cap is per learner.
        send(two, WEBM, true, 10).andExpect(status().isOk());
    }

    @Test
    void theTypeCheckReadsTheFileNotTheClaim() {
        assertThat(VoiceService.kindOf(WEBM).extension()).isEqualTo("webm");
        assertThat(VoiceService.kindOf("OggS....".getBytes()).extension()).isEqualTo("ogg");
        assertThat(VoiceService.kindOf("RIFF....WAVEfmt ".getBytes()).extension()).isEqualTo("wav");
        assertThat(VoiceService.kindOf("....ftypM4A ".getBytes()).extension()).isEqualTo("m4a");
        assertThat(VoiceService.kindOf("ID3....".getBytes()).extension()).isEqualTo("mp3");
        assertThat(VoiceService.kindOf("%PDF-1.7 not audio".getBytes())).isNull();
    }
}
