package com.secureportal.api;

import com.secureportal.auth.AppPrincipal;
import com.secureportal.course.AdminNotALearnerException;
import com.secureportal.interview.InterviewResume;
import com.secureportal.interview.InvalidInterviewException;
import com.secureportal.interview.ResumeService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;

/** A learner's own resume for interview practice. Only they can see, replace or delete it. */
@RestController
@RequestMapping("/api/interviews/resume")
public class ResumeApiController {

    /** The resume's details and a short preview of the text read from it; the full text never leaves the server. */
    public record ResumeDto(String filename, Instant uploadedAt, Instant expiresAt, int characters, String preview) {
        static ResumeDto of(InterviewResume resume) {
            return new ResumeDto(resume.getOriginalFilename(), resume.getUploadedAt(), resume.getExpiresAt(), resume.getCharacters(),
                    resume.preview());
        }
    }

    private final ResumeService resumeService;

    public ResumeApiController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    /** The current resume, or 204 when there isn't one. */
    @GetMapping
    public org.springframework.http.ResponseEntity<ResumeDto> current(@AuthenticationPrincipal AppPrincipal principal) {
        requireLearner(principal);
        return resumeService.find(principal.getUserId()).map(r -> org.springframework.http.ResponseEntity.ok(ResumeDto.of(r)))
                .orElseGet(() -> org.springframework.http.ResponseEntity.noContent().build());
    }

    @PostMapping
    public ResumeDto upload(@RequestParam("file") MultipartFile file, @RequestParam(defaultValue = "false") boolean consent,
                            @AuthenticationPrincipal AppPrincipal principal) {
        requireLearner(principal);
        if (file.getSize() > ResumeService.MAX_BYTES_ALLOWED) {
            throw new InvalidInterviewException("The resume must be under 3 MB.");
        }
        try {
            return ResumeDto.of(resumeService.upload(principal.getUserId(), file.getOriginalFilename(), file.getBytes(), consent));
        } catch (IOException e) {
            throw new InvalidInterviewException("We couldn't read that file. Please try again.");
        }
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AppPrincipal principal) {
        requireLearner(principal);
        resumeService.delete(principal.getUserId());
    }

    private static void requireLearner(AppPrincipal principal) {
        if (principal.isAdmin()) {
            throw new AdminNotALearnerException();
        }
    }
}
