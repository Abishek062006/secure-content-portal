package com.secureportal.user;

import com.secureportal.auth.AppPrincipal;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    private static final int PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final UserService userService;

    public UserAdminController(UserRepository userRepository, UserService userService) {
        this.userRepository = userRepository;
        this.userService = userService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String search,
                        @AuthenticationPrincipal AppPrincipal principal, Model model) {
        Pageable pageable = PageRequest.of(0, PAGE_SIZE, Sort.by(Sort.Direction.ASC, "email"));
        String query = (search == null) ? "" : search;

        model.addAttribute("users", userRepository.search(query, pageable).getContent());
        model.addAttribute("search", (search == null || search.isBlank()) ? null : search);
        model.addAttribute("currentUserId", principal.getUserId());
        return "admin/users";
    }

    @PostMapping("/{id}/promote")
    public String promote(@PathVariable Long id, @AuthenticationPrincipal AppPrincipal principal,
                           RedirectAttributes redirectAttributes) {
        userService.promoteToAdmin(id, principal.getEmail());
        redirectAttributes.addFlashAttribute("successMessage", "Admin access granted.");
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/demote")
    public String demote(@PathVariable Long id, @AuthenticationPrincipal AppPrincipal principal,
                          RedirectAttributes redirectAttributes) {
        userService.demoteToViewer(id, principal.getEmail(), principal.getUserId());
        redirectAttributes.addFlashAttribute("successMessage", "Admin access revoked.");
        return "redirect:/admin/users";
    }
}
