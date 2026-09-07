package com.secureportal.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Turns upload failures into a redirect back to the form with a readable
 * message, instead of a stack trace or a blank screen.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UploadException.class)
    public String handleUploadException(UploadException ex, HttpServletRequest request,
                                         RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("uploadError", ex.getMessage());
        return "redirect:" + refererOrFallback(request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request,
                                       RedirectAttributes redirectAttributes) {
        log.info("Upload rejected: request exceeded the configured size limit");
        redirectAttributes.addFlashAttribute("uploadError", "That file is too large to upload.");
        return "redirect:" + refererOrFallback(request);
    }

    private String refererOrFallback(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        return referer != null ? referer : "/admin/content";
    }
}
