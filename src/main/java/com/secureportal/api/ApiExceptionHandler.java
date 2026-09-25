package com.secureportal.api;

import com.secureportal.common.UploadException;
import com.secureportal.content.ContentNotFoundException;
import com.secureportal.course.CourseNotFoundException;
import com.secureportal.course.CourseStructureException;
import com.secureportal.course.EnrollmentRequiredException;
import com.secureportal.course.LessonNotFoundException;
import com.secureportal.course.ModuleNotFoundException;
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
