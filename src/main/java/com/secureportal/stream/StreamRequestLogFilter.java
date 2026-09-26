package com.secureportal.stream;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** One line per video/media delivery request saying how it ended, so a player that "could not load" can be diagnosed. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StreamRequestLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(StreamRequestLogFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/course-stream/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().replaceFirst("^(/api/course-stream/)[^/]+", "$1<ticket>");
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            log.warn("{} range={} -> failed: {}", path, request.getHeader("Range"), e.toString());
            throw e;
        }
        log.info("{} range={} -> {} {} sessionCookie={} user={}", path, request.getHeader("Range"), response.getStatus(),
                response.getContentType(), request.getHeader("Cookie") != null && request.getHeader("Cookie").contains("SESSION="),
                request.getUserPrincipal() != null);
    }
}
