package com.secureportal.infra;

import com.secureportal.auth.AppPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * Stops one account (or address) from hammering the API. Each rule names a method and path, a per-minute budget and
 * is counted per user; everything else under /api shares a generous overall budget. Media delivery is exempt because
 * a single page legitimately makes dozens of range and image requests.
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private record Rule(HttpMethod method, String pattern, String name, int perMinute) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule(HttpMethod.POST, "/api/admin/lessons/*/questions/generate", "generate", 6),
            new Rule(HttpMethod.POST, "/api/posts", "post", 8),
            new Rule(HttpMethod.POST, "/api/posts/*/comments", "comment", 20),
            new Rule(HttpMethod.PUT, "/api/posts/*/reaction", "react", 60),
            new Rule(HttpMethod.POST, "/api/courses/*/enroll", "enroll", 30),
            new Rule(HttpMethod.POST, "/api/assessments/*/attempts", "attempt", 20),
            new Rule(HttpMethod.PUT, "/api/attempts/*/answers", "answer", 240),
            // Each interview step calls the AI, so these are the tightest limits in the app.
            new Rule(HttpMethod.POST, "/api/interviews/start", "interview-start", 5),
            new Rule(HttpMethod.POST, "/api/interviews/sessions/*/answer", "interview-answer", 12),
            new Rule(HttpMethod.POST, "/api/interviews/sessions/*/complete", "interview-complete", 10),
            new Rule(HttpMethod.POST, "/api/gamification/check-in", "check-in", 10),
            new Rule(HttpMethod.POST, "/api/hackathons/*/register", "hackathon-register", 20),
            new Rule(HttpMethod.POST, "/api/admin/gamification/adjust", "xp-adjust", 30),
            new Rule(HttpMethod.GET, "/api/gamification/leaderboard", "leaderboard", 60));

    private static final List<String> EXEMPT = List.of("/api/stream/**", "/api/course-stream/**", "/api/post-stream/**",
            "/api/material/**", "/api/pdf/**", "/api/html/**", "/api/**/thumbnail", "/api/posts/*/image",
            "/api/profiles/*/avatar", "/api/profiles/*/banner");

    static final int OVERALL_PER_MINUTE = 1500;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final RateLimiter limiter;

    public RateLimitInterceptor(RateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String path = request.getRequestURI();
        if (EXEMPT.stream().anyMatch(p -> matcher.match(p, path))) {
            return true;
        }
        String who = who(request);
        for (Rule rule : RULES) {
            if (rule.method().matches(request.getMethod()) && matcher.match(rule.pattern(), path)) {
                RateLimiter.Result result = limiter.hit(rule.name() + ":" + who, rule.perMinute(), WINDOW);
                if (!result.allowed()) {
                    return reject(response, result);
                }
            }
        }
        RateLimiter.Result overall = limiter.hit("all:" + who, OVERALL_PER_MINUTE, WINDOW);
        return overall.allowed() || reject(response, overall);
    }

    private static boolean reject(HttpServletResponse response, RateLimiter.Result result) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"You're doing that too often. Try again in " + result.retryAfterSeconds() + " seconds.\"}");
        return false;
    }

    /** The signed-in user, else the client address. */
    private static String who(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppPrincipal principal) {
            return "u" + principal.getUserId();
        }
        return "ip" + clientAddress(request);
    }

    /**
     * Behind CloudFront the socket peer is a CloudFront edge, shared by unrelated visitors. CloudFront overwrites
     * {@code CloudFront-Viewer-Address} ("ip:port") with the real visitor, and in production the load balancer only
     * accepts traffic that came through CloudFront, so the header can be believed there.
     */
    static String clientAddress(HttpServletRequest request) {
        String viewer = request.getHeader("CloudFront-Viewer-Address");
        if (viewer != null && !viewer.isBlank()) {
            int colon = viewer.lastIndexOf(':');
            return colon > 0 ? viewer.substring(0, colon) : viewer;
        }
        return request.getRemoteAddr();
    }
}
