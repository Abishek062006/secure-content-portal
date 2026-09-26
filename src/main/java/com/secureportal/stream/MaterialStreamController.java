package com.secureportal.stream;

import com.secureportal.course.CourseMaterial;
import com.secureportal.course.CourseMaterialRepository;
import com.secureportal.course.MaterialKind;
import com.secureportal.pdf.PdfRenderService;
import com.secureportal.storage.StorageObject;
import com.secureportal.storage.StorageService;
import com.secureportal.user.User;
import com.secureportal.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

/**
 * Delivers a module material for viewing inside the portal, using the same protections as library content:
 * watermarked page images for PDFs, sandboxed HTML, and range-streamed video, all behind a session-bound ticket.
 */
@RestController
public class MaterialStreamController {

    private final TicketGuard ticketGuard;
    private final CourseMaterialRepository materialRepository;
    private final UserRepository userRepository;
    private final PdfRenderService pdfRenderService;
    private final StorageService storageService;
    private final RangedMediaResponder mediaResponder;

    public MaterialStreamController(TicketGuard ticketGuard, CourseMaterialRepository materialRepository,
                                    UserRepository userRepository, PdfRenderService pdfRenderService,
                                    StorageService storageService, RangedMediaResponder mediaResponder) {
        this.ticketGuard = ticketGuard;
        this.materialRepository = materialRepository;
        this.userRepository = userRepository;
        this.pdfRenderService = pdfRenderService;
        this.storageService = storageService;
        this.mediaResponder = mediaResponder;
    }

    @GetMapping(value = "/api/material/{ticket}/pdf/{page}", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> pdfPage(@PathVariable String ticket, @PathVariable int page, HttpServletRequest request) {
        StreamTicket streamTicket = ticketGuard.verify(ticket, request, StreamTicket.Purpose.MATERIAL);
        CourseMaterial material = material(streamTicket, MaterialKind.PDF);
        User viewer = userRepository.findById(streamTicket.userId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        byte[] jpeg = pdfRenderService.renderPage(material.getId(), material.getStorageKey(), material.getPageCount(), page, viewer.getEmail());
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CACHE_CONTROL, "no-store");
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline");
        headers.add("X-Content-Type-Options", "nosniff");
        return ResponseEntity.ok().headers(headers).contentType(MediaType.IMAGE_JPEG).body(jpeg);
    }

    @GetMapping("/api/material/{ticket}/html")
    public ResponseEntity<byte[]> html(@PathVariable String ticket, HttpServletRequest request) {
        StreamTicket streamTicket = ticketGuard.verify(ticket, request, StreamTicket.Purpose.MATERIAL);
        CourseMaterial material = material(streamTicket, MaterialKind.HTML);
        byte[] content;
        try (StorageObject object = storageService.get(material.getStorageKey(), null, null)) {
            content = object.content().readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read HTML material " + material.getId(), e);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CACHE_CONTROL, "no-store");
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline");
        headers.add("X-Content-Type-Options", "nosniff");
        headers.add("Content-Security-Policy", "sandbox; default-src 'none'; style-src 'unsafe-inline'; img-src data: https:;");
        return ResponseEntity.ok().headers(headers).contentType(MediaType.TEXT_HTML).body(content);
    }

    @GetMapping("/api/material/{ticket}/video")
    public ResponseEntity<InputStreamResource> video(@PathVariable String ticket, HttpServletRequest request) {
        StreamTicket streamTicket = ticketGuard.verify(ticket, request, StreamTicket.Purpose.MATERIAL);
        CourseMaterial material = material(streamTicket, MaterialKind.VIDEO);
        return mediaResponder.respond(material.getStorageKey(), material.getMime(), request);
    }

    private CourseMaterial material(StreamTicket ticket, MaterialKind expected) {
        return materialRepository.findById(ticket.contentId())
                .filter(m -> m.getKind() == expected && m.getStorageKey() != null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
