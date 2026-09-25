package com.secureportal.api;

import com.secureportal.ai.AiException;
import com.secureportal.assessment.AssessmentAccessException;
import com.secureportal.assessment.AssessmentNotFoundException;
import com.secureportal.assessment.AttemptClosedException;
import com.secureportal.assessment.AttemptNotFoundException;
import com.secureportal.assessment.InvalidAssessmentException;
import com.secureportal.ai.AiNotConfiguredException;
import com.secureportal.common.UploadException;
import com.secureportal.content.ContentNotFoundException;
import com.secureportal.course.CourseNotFoundException;
import com.secureportal.course.CourseStructureException;
import com.secureportal.course.EnrollmentRequiredException;
import com.secureportal.course.LessonNotFoundException;
import com.secureportal.course.ModuleNotFoundException;
import com.secureportal.quiz.InvalidQuestionException;
import com.secureportal.quiz.QuestionGenerationException;
import com.secureportal.quiz.QuestionNotFoundException;
import com.secureportal.user.UserManagementException;
import com.secureportal.user.UserNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** Maps the app's known exceptions to a JSON body with an appropriate status code. */
@RestControllerAdvice(basePackages = "com.secureportal.api")
public class ApiExceptionHandler {

    @ExceptionHandler(ContentNotFoundException.class)
    public ResponseEntity<ApiError> handleContentNotFound(ContentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(ex.getMessage()));
    }

    @ExceptionHandler(CourseNotFoundException.class)
    public ResponseEntity<ApiError> handleCourseNotFound(CourseNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(ex.getMessage()));
    }

    @ExceptionHandler({ModuleNotFoundException.class, LessonNotFoundException.class})
    public ResponseEntity<ApiError> handleStructureNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(ex.getMessage()));
    }

    @ExceptionHandler(CourseStructureException.class)
    public ResponseEntity<ApiError> handleCourseStructure(CourseStructureException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(ex.getMessage()));
    }

    @ExceptionHandler(EnrollmentRequiredException.class)
    public ResponseEntity<ApiError> handleEnrollmentRequired(EnrollmentRequiredException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError(ex.getMessage()));
    }

    @ExceptionHandler(QuestionNotFoundException.class)
    public ResponseEntity<ApiError> handleQuestionNotFound(QuestionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(ex.getMessage()));
    }

    @ExceptionHandler({InvalidQuestionException.class, QuestionGenerationException.class})
    public ResponseEntity<ApiError> handleBadQuestion(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(ex.getMessage()));
    }

    @ExceptionHandler(AiNotConfiguredException.class)
    public ResponseEntity<ApiError> handleAiNotConfigured(AiNotConfiguredException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiError(ex.getMessage()));
    }

    @ExceptionHandler(AiException.class)
    public ResponseEntity<ApiError> handleAi(AiException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ApiError(ex.getMessage()));
    }

    @ExceptionHandler({AssessmentNotFoundException.class, AttemptNotFoundException.class})
    public ResponseEntity<ApiError> handleAssessmentNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(ex.getMessage()));
    }

    @ExceptionHandler(InvalidAssessmentException.class)
    public ResponseEntity<ApiError> handleInvalidAssessment(InvalidAssessmentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(ex.getMessage()));
    }

    @ExceptionHandler(AssessmentAccessException.class)
    public ResponseEntity<ApiError> handleAssessmentAccess(AssessmentAccessException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError(ex.getMessage()));
    }

    @ExceptionHandler(AttemptClosedException.class)
    public ResponseEntity<ApiError> handleAttemptClosed(AttemptClosedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(ex.getMessage()));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiError> handleUserNotFound(UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(ex.getMessage()));
    }

    @ExceptionHandler(UploadException.class)
    public ResponseEntity<ApiError> handleUploadException(UploadException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(ex.getMessage()));
    }

    @ExceptionHandler(UserManagementException.class)
    public ResponseEntity<ApiError> handleUserManagementException(UserManagementException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(ex.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(new ApiError("That file is too large to upload."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getDefaultMessage())
                .orElse("Invalid request.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(message));
    }
}
