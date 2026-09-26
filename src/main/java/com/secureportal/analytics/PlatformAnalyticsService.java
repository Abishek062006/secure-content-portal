package com.secureportal.analytics;

import com.secureportal.analytics.PlatformAnalytics.Activity;
import com.secureportal.analytics.PlatformAnalytics.Community;
import com.secureportal.analytics.PlatformAnalytics.Content;
import com.secureportal.analytics.PlatformAnalytics.CourseRow;
import com.secureportal.analytics.PlatformAnalytics.Funnel;
import com.secureportal.analytics.PlatformAnalytics.Quiz;
import com.secureportal.analytics.PlatformAnalytics.Series;
import com.secureportal.analytics.PlatformAnalytics.Totals;
import com.secureportal.infra.TtlCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregates for the admin dashboard, read straight from the tables with a handful of queries. All times in the
 * database are UTC, and the windows are computed in SQL from UTC_TIMESTAMP(6) so the server's time zone can't shift them.
 */
@Service
public class PlatformAnalyticsService {

    static final int DAYS = 30;

    private static final String NOT_ADMIN = "role <> 'ADMIN'";
    private static final String SINCE_30 = "DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 30 DAY)";
    private static final String SINCE_7 = "DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 7 DAY)";

    private static final String COMPLETED_ENROLLMENT = """
            (SELECT COUNT(*) FROM lessons l WHERE l.course_id = e.course_id) > 0
            AND (SELECT COUNT(*) FROM lesson_progress p WHERE p.user_id = e.user_id AND p.course_id = e.course_id AND p.completed = TRUE)
                = (SELECT COUNT(*) FROM lessons l2 WHERE l2.course_id = e.course_id)""";

    private final JdbcTemplate jdbc;
    private final TtlCache cache;
    private final Duration cacheTtl;

    public PlatformAnalyticsService(JdbcTemplate jdbc, TtlCache cache,
                                    @Value("${app.cache.analytics-seconds:60}") long cacheSeconds) {
        this.jdbc = jdbc;
        this.cache = cache;
        this.cacheTtl = Duration.ofSeconds(cacheSeconds);
    }

    /** The dashboard is many aggregate queries, so it's cached briefly; a few seconds of staleness is fine here. */
    public PlatformAnalytics compute() {
        return cache.get("platform-analytics", cacheTtl, this::load);
    }

    private PlatformAnalytics load() {
        return new PlatformAnalytics(totals(), funnel(), quiz(), content(), community(), topCourses(), series(), recent());
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private Totals totals() {
        long active7 = count("SELECT COUNT(DISTINCT u) FROM ("
                + "SELECT user_id u FROM lesson_progress WHERE updated_at >= " + SINCE_7
                + " UNION SELECT user_id FROM attempts WHERE started_at >= " + SINCE_7
                + " UNION SELECT user_id FROM enrollments WHERE enrolled_at >= " + SINCE_7 + ") t");
        return new Totals(
                count("SELECT COUNT(*) FROM users WHERE " + NOT_ADMIN),
                count("SELECT COUNT(*) FROM users WHERE " + NOT_ADMIN + " AND created_at >= " + SINCE_30),
                active7,
                count("SELECT COUNT(*) FROM enrollments"),
                count("SELECT COUNT(*) FROM enrollments WHERE enrolled_at >= " + SINCE_30),
                count("SELECT COUNT(*) FROM courses WHERE status = 'PUBLISHED'"),
                count("SELECT COUNT(*) FROM courses WHERE status <> 'PUBLISHED'"),
                count("SELECT COUNT(*) FROM certificates"),
                count("SELECT COUNT(*) FROM certificates WHERE issued_at >= " + SINCE_30));
    }

    private Funnel funnel() {
        return new Funnel(
                count("SELECT COUNT(*) FROM enrollments"),
                count("SELECT COUNT(*) FROM enrollments e WHERE EXISTS (SELECT 1 FROM lesson_progress p "
                        + "WHERE p.user_id = e.user_id AND p.course_id = e.course_id AND p.completed = TRUE)"),
                count("SELECT COUNT(*) FROM enrollments e WHERE " + COMPLETED_ENROLLMENT),
                count("SELECT COUNT(*) FROM certificates"));
    }

    private Quiz quiz() {
        Double average = jdbc.queryForObject("SELECT AVG(score_percent) FROM attempts WHERE status = 'SUBMITTED' AND score_percent IS NOT NULL", Double.class);
        Long graded = jdbc.queryForObject("SELECT COUNT(*) FROM attempts WHERE status = 'SUBMITTED' AND passed IS NOT NULL", Long.class);
        Long passed = jdbc.queryForObject("SELECT COUNT(*) FROM attempts WHERE status = 'SUBMITTED' AND passed = TRUE", Long.class);
        return new Quiz(count("SELECT COUNT(*) FROM attempts WHERE status = 'SUBMITTED'"),
                count("SELECT COUNT(*) FROM attempts WHERE status = 'SUBMITTED' AND started_at >= " + SINCE_30),
                average, graded == null || graded == 0 ? null : 100.0 * (passed == null ? 0 : passed) / graded);
    }

    private Content content() {
        return new Content(count("SELECT COUNT(*) FROM lessons"), count("SELECT COUNT(*) FROM module_materials"),
                count("SELECT COUNT(*) FROM questions"), count("SELECT COUNT(*) FROM questions WHERE status = 'APPROVED'"),
                count("SELECT COUNT(*) FROM questions WHERE source = 'AI'"));
    }

    private Community community() {
        Map<String, Long> byType = new LinkedHashMap<>();
        for (String type : List.of("LIKE", "CELEBRATE", "SUPPORT", "LOVE", "INSIGHTFUL", "FUNNY")) {
            byType.put(type, 0L);
        }
        jdbc.query("SELECT type, COUNT(*) FROM post_reactions GROUP BY type", rs -> {
            byType.put(rs.getString(1), rs.getLong(2));
        });
        return new Community(count("SELECT COUNT(*) FROM posts WHERE publish_at <= UTC_TIMESTAMP(6)"),
                count("SELECT COUNT(*) FROM posts WHERE publish_at <= UTC_TIMESTAMP(6) AND created_at >= " + SINCE_30),
                count("SELECT COUNT(*) FROM post_comments"), count("SELECT COUNT(*) FROM post_reactions"), byType);
    }

    private List<CourseRow> topCourses() {
        String sql = """
                SELECT c.id, c.title, c.category,
                       (SELECT COUNT(*) FROM enrollments e WHERE e.course_id = c.id) AS enrolled,
                       (SELECT COUNT(*) FROM enrollments e WHERE e.course_id = c.id AND %s) AS completed,
                       (SELECT COUNT(*) FROM certificates ct WHERE ct.course_id = c.id) AS certified
                FROM courses c WHERE c.status = 'PUBLISHED'
                ORDER BY enrolled DESC, c.created_at DESC LIMIT 6""".formatted(COMPLETED_ENROLLMENT);
        return jdbc.query(sql, (rs, i) -> new CourseRow(uuid(rs.getBytes("id")), rs.getString("title"), rs.getString("category"),
                rs.getLong("enrolled"), rs.getLong("completed"), rs.getLong("certified")));
    }

    private Series series() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<String> dates = new ArrayList<>();
        for (int i = DAYS - 1; i >= 0; i--) {
            dates.add(today.minusDays(i).toString());
        }
        return new Series(dates,
                daily("SELECT DATE(enrolled_at), COUNT(*) FROM enrollments WHERE enrolled_at >= " + SINCE_30 + " GROUP BY DATE(enrolled_at)", dates),
                daily("SELECT DATE(created_at), COUNT(*) FROM users WHERE " + NOT_ADMIN + " AND created_at >= " + SINCE_30 + " GROUP BY DATE(created_at)", dates),
                daily("SELECT DATE(started_at), COUNT(*) FROM attempts WHERE started_at >= " + SINCE_30 + " GROUP BY DATE(started_at)", dates),
                daily("SELECT DATE(created_at), COUNT(*) FROM posts WHERE created_at >= " + SINCE_30 + " GROUP BY DATE(created_at)", dates));
    }

    private List<Integer> daily(String sql, List<String> dates) {
        Map<String, Integer> byDay = new HashMap<>();
        jdbc.query(sql, rs -> {
            byDay.put(rs.getDate(1).toLocalDate().toString(), rs.getInt(2));
        });
        return dates.stream().map(d -> byDay.getOrDefault(d, 0)).toList();
    }

    private List<Activity> recent() {
        return jdbc.query("SELECT actor_email, action, detail, created_at FROM audit_logs ORDER BY created_at DESC LIMIT 8",
                (rs, i) -> {
                    Timestamp at = rs.getTimestamp("created_at");
                    return new Activity(rs.getString("actor_email"), rs.getString("action"), rs.getString("detail"),
                            at == null ? null : at.toLocalDateTime().toInstant(ZoneOffset.UTC));
                });
    }

    private static UUID uuid(byte[] bytes) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
