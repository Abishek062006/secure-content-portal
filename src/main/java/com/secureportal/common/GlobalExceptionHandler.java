package com.secureportal.common;

import com.secureportal.content.AdminContentController;
import com.secureportal.user.UserAdminController;
import com.secureportal.user.UserManagementException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Turns a handful of expected failure cases into a redirect back to the
 * page with a readable message, instead of a stack trace or a blank screen.
 *
 * <p>Scoped to just the two Thymeleaf admin controllers — {@link com.secureportal.api}
 * has its own {@code ApiExceptionHandler} returning JSON for the same exception types,
 * and an unscoped advice here would collide with it.
 */
@ControllerAdvice(basePackageClasses = {AdminContentController.class, UserAdminController.class})
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UploadException.class)
    public String handleUploadException(UploadException ex, HttpServletRequest request,
                                         RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:" + refererOrFallback(request, "/admin/content");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request,
                                       RedirectAttributes redirectAttributes) {
        log.info("Upload rejected: request exceeded the configured size limit");
        redirectAttributes.addFlashAttribute("errorMessage", "That file is too large to upload.");
        return "redirect:" + refererOrFallback(request, "/admin/content");
    }

    @ExceptionHandler(UserManagementException.class)
    public String handleUserManagementException(UserManagementException ex, HttpServletRequest request,
                                                  RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:" + refererOrFallback(request, "/admin/users");
    }

    private String refererOrFallback(HttpServletRequest request, String fallback) {
        String referer = request.getHeader("Referer");
        return referer != null ? referer : fallback;
    }
}
