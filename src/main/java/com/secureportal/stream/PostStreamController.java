package com.secureportal.stream;

import com.secureportal.feed.Post;
import com.secureportal.feed.PostRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Ticket-gated, range-capable delivery of a feed post's video, like a lesson's video. */
@RestController
public class PostStreamController {

    private final TicketGuard ticketGuard;
    private final PostRepository postRepository;
    private final RangedMediaResponder mediaResponder;

    public PostStreamController(TicketGuard ticketGuard, PostRepository postRepository, RangedMediaResponder mediaResponder) {
        this.ticketGuard = ticketGuard;
        this.postRepository = postRepository;
        this.mediaResponder = mediaResponder;
    }

    @GetMapping("/api/post-stream/{ticket}")
    public ResponseEntity<InputStreamResource> stream(@PathVariable String ticket, HttpServletRequest request) {
        StreamTicket streamTicket = ticketGuard.verify(ticket, request, StreamTicket.Purpose.POST_VIDEO);
        Post post = postRepository.findById(streamTicket.contentId())
                .filter(p -> p.getVideoKey() != null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return mediaResponder.respond(post.getVideoKey(), post.getVideoMime(), request);
    }
}
