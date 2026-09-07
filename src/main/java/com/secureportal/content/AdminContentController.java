package com.secureportal.content;

import com.secureportal.audit.AuditService;
import com.secureportal.auth.AppPrincipal;
import com.secureportal.content.dto.EditForm;
import com.secureportal.content.dto.UploadForm;
import com.secureportal.user.User;
import com.secureportal.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * URL-level access is already enforced by SecurityConfig ({@code /admin/**}
 * requires ROLE_ADMIN). The {@code @PreAuthorize} here is a second,
 * independent check at the method level — defence in depth, so a future
 * routing mistake can't quietly expose an admin operation.
 */
@Controller
@RequestMapping("/admin/content")
@PreAuthorize("hasRole('ADMIN')")
public class AdminContentController {

    private final ContentRepository contentRepository;
    private final ContentService contentService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public AdminContentController(ContentRepository contentRepository, ContentService contentService,
                                   UserRepository userRepository, AuditService auditService) {
        this.contentRepository = contentRepository;
        this.contentService = contentService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("items", contentRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")));
        return "admin/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        if (!model.containsAttribute("uploadForm")) {
            model.addAttribute("uploadForm", new UploadForm());
        }
        model.addAttribute("contentTypes", ContentType.values());
        return "admin/upload";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("uploadForm") UploadForm form, BindingResult bindingResult,
                          @AuthenticationPrincipal AppPrincipal principal, Model model,
                          RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("contentTypes", ContentType.values());
            return "admin/upload";
        }

        User uploadedBy = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new IllegalStateException("Signed-in user not found: " + principal.getUserId()));

        ContentItem created = contentService.create(form, uploadedBy);
        auditService.log(principal.getEmail(), "UPLOAD", created.getId(),
                created.getContentType() + " \"" + created.getTitle() + "\"");
        redirectAttributes.addFlashAttribute("successMessage", "\"" + created.getTitle() + "\" uploaded.");
        return "redirect:/admin/content";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model) {
        ContentItem item = contentRepository.findById(id)
                .orElseThrow(() -> new ContentNotFoundException(id));

        if (!model.containsAttribute("editForm")) {
            EditForm form = new EditForm();
            form.setTitle(item.getTitle());
            form.setDescription(item.getDescription());
            form.setCategory(item.getCategory());
            model.addAttribute("editForm", form);
        }
        model.addAttribute("item", item);
        return "admin/edit";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable UUID id, @Valid @ModelAttribute("editForm") EditForm form,
                          BindingResult bindingResult, @AuthenticationPrincipal AppPrincipal principal,
                          Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("item", contentRepository.findById(id)
                    .orElseThrow(() -> new ContentNotFoundException(id)));
            return "admin/edit";
        }

        ContentItem updated = contentService.update(id, form);
        auditService.log(principal.getEmail(), "EDIT", updated.getId(), "\"" + updated.getTitle() + "\"");
        redirectAttributes.addFlashAttribute("successMessage", "\"" + updated.getTitle() + "\" updated.");
        return "redirect:/admin/content";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, @AuthenticationPrincipal AppPrincipal principal,
                          RedirectAttributes redirectAttributes) {
        ContentItem item = contentRepository.findById(id)
                .orElseThrow(() -> new ContentNotFoundException(id));
        String title = item.getTitle();
        ContentType type = item.getContentType();

        contentService.delete(id);
        auditService.log(principal.getEmail(), "DELETE", id, type + " \"" + title + "\"");
        redirectAttributes.addFlashAttribute("successMessage", "Item deleted.");
        return "redirect:/admin/content";
    }
}
