package com.secureportal.api;

import com.secureportal.api.dto.EnquiryDto;
import com.secureportal.audit.AuditService;
import com.secureportal.auth.AppPrincipal;
import com.secureportal.course.CourseEnquiry;
import com.secureportal.course.CourseEnquiryService;
import com.secureportal.course.CourseRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** The admin's inbox of "enquire" submissions, across every course. */
@RestController
@RequestMapping("/api/admin/enquiries")
@PreAuthorize("hasRole('ADMIN')")
public class AdminEnquiryApiController {

    private final CourseEnquiryService enquiryService;
    private final CourseRepository courseRepository;
    private final AuditService auditService;

    public AdminEnquiryApiController(CourseEnquiryService enquiryService, CourseRepository courseRepository,
                                     AuditService auditService) {
        this.enquiryService = enquiryService;
        this.courseRepository = courseRepository;
        this.auditService = auditService;
    }

    @GetMapping
    public List<EnquiryDto> list(@RequestParam(required = false) String status) {
        List<CourseEnquiry> enquiries = "NEW".equalsIgnoreCase(status) ? enquiryService.newOnes() : enquiryService.all();
        Set<UUID> courseIds = enquiries.stream().map(CourseEnquiry::getCourseId).collect(Collectors.toSet());
        Map<UUID, String> titles = new HashMap<>();
        courseRepository.findAllById(courseIds).forEach(c -> titles.put(c.getId(), c.getTitle()));
        return enquiries.stream().map(e -> EnquiryDto.of(e, titles.get(e.getCourseId()))).toList();
    }

    @PostMapping("/{id}/contacted")
    public EnquiryDto markContacted(@PathVariable Long id, @AuthenticationPrincipal AppPrincipal principal) {
        CourseEnquiry enquiry = enquiryService.markContacted(id, principal.getEmail());
        auditService.log(principal.getEmail(), "ENQUIRY_CONTACTED", enquiry.getCourseId(),
                "Marked enquiry from " + enquiry.getEmail() + " as contacted");
        return EnquiryDto.of(enquiry, courseRepository.findById(enquiry.getCourseId()).map(c -> c.getTitle()).orElse(null));
    }
}
