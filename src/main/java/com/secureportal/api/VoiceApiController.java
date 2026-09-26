package com.secureportal.api;

import com.secureportal.auth.AppPrincipal;
import com.secureportal.course.AdminNotALearnerException;
import com.secureportal.interview.InvalidInterviewException;
import com.secureportal.interview.VoiceService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** Turns a spoken interview answer into text the learner can read and edit. The recording is never stored. */
@RestController
@RequestMapping("/api/interviews/transcribe")
public class VoiceApiController {

    private final VoiceService voiceService;

    public VoiceApiController(VoiceService voiceService) {
        this.voiceService = voiceService;
    }

    @PostMapping
    public VoiceService.Transcript transcribe(@RequestParam("audio") MultipartFile audio,
                                              @RequestParam(defaultValue = "false") boolean consent,
                                              @RequestParam(defaultValue = "0") int durationSeconds,
                                              @AuthenticationPrincipal AppPrincipal principal) {
        if (principal.isAdmin()) {
            throw new AdminNotALearnerException();
        }
        if (!consent) {
            throw new InvalidInterviewException("Please confirm you're happy for your voice recording to be sent to our speech service.");
        }
        if (audio.getSize() > VoiceService.MAX_BYTES_ALLOWED) {
            throw new InvalidInterviewException("That recording is too long. Keep each answer under 3 minutes.");
        }
        try {
            return voiceService.transcribe(principal.getUserId(), audio.getBytes(), durationSeconds);
        } catch (IOException e) {
            throw new InvalidInterviewException("We couldn't read that recording. Please try again.");
        }
    }
}
