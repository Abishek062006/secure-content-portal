package com.secureportal.api;

import com.secureportal.auth.AppPrincipal;
import com.secureportal.course.AdminNotALearnerException;
import com.secureportal.course.EnrollmentRequiredException;
import com.secureportal.course.LearningService;
import com.secureportal.course.LessonNote;
import com.secureportal.course.LessonNoteRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A learner's own notes on a lesson. Nobody else, admins included, can read or change them. */
@RestController
@RequestMapping("/api/courses/{courseId}/lessons/{lessonId}/notes")
public class LessonNoteApiController {

    public record NoteRequest(@NotBlank @Size(max = LessonNote.MAX_LENGTH) String body, @Min(0) int seconds) {
    }

    public record EditRequest(@NotBlank @Size(max = LessonNote.MAX_LENGTH) String body) {
    }

    public record NoteDto(Long id, int seconds, String body, Instant updatedAt) {
        static NoteDto of(LessonNote note) {
            return new NoteDto(note.getId(), note.getVideoSeconds(), note.getBody(), note.getUpdatedAt());
        }
    }

    private static final int MAX_NOTES_PER_LESSON = 500;

    private final LearningService learningService;
    private final LessonNoteRepository notes;

    public LessonNoteApiController(LearningService learningService, LessonNoteRepository notes) {
        this.learningService = learningService;
        this.notes = notes;
    }

    @GetMapping
    public List<NoteDto> list(@PathVariable UUID courseId, @PathVariable UUID lessonId,
                              @AuthenticationPrincipal AppPrincipal principal) {
        if (principal.isAdmin()) {
            return List.of(); // admins preview; they have no notes
        }
        requireEnrolledLearner(courseId, lessonId, principal);
        return notes.findByUserIdAndLessonIdOrderByVideoSecondsAscIdAsc(principal.getUserId(), lessonId)
                .stream().map(NoteDto::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NoteDto add(@PathVariable UUID courseId, @PathVariable UUID lessonId, @Valid @RequestBody NoteRequest request,
                       @AuthenticationPrincipal AppPrincipal principal) {
        requireEnrolledLearner(courseId, lessonId, principal);
        if (notes.findByUserIdAndLessonIdOrderByVideoSecondsAscIdAsc(principal.getUserId(), lessonId).size() >= MAX_NOTES_PER_LESSON) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That's the most notes one lesson can hold.");
        }
        return NoteDto.of(notes.save(new LessonNote(principal.getUserId(), lessonId, courseId, request.seconds(), request.body().strip())));
    }

    @PutMapping("/{noteId}")
    public NoteDto edit(@PathVariable UUID courseId, @PathVariable UUID lessonId, @PathVariable Long noteId,
                        @Valid @RequestBody EditRequest request, @AuthenticationPrincipal AppPrincipal principal) {
        requireEnrolledLearner(courseId, lessonId, principal);
        LessonNote note = mine(noteId, lessonId, principal);
        note.edit(request.body().strip());
        return NoteDto.of(notes.save(note));
    }

    @DeleteMapping("/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID courseId, @PathVariable UUID lessonId, @PathVariable Long noteId,
                       @AuthenticationPrincipal AppPrincipal principal) {
        requireEnrolledLearner(courseId, lessonId, principal);
        notes.delete(mine(noteId, lessonId, principal));
    }

    private LessonNote mine(Long noteId, UUID lessonId, AppPrincipal principal) {
        return notes.findByIdAndUserIdAndLessonId(noteId, principal.getUserId(), lessonId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Note not found"));
    }

    private void requireEnrolledLearner(UUID courseId, UUID lessonId, AppPrincipal principal) {
        if (principal.isAdmin()) {
            throw new AdminNotALearnerException();
        }
        learningService.visibleCourse(courseId, false);
        learningService.lessonOf(courseId, lessonId);
        if (learningService.enrollment(principal.getUserId(), courseId).isEmpty()) {
            throw new EnrollmentRequiredException();
        }
    }
}
