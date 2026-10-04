package com.secureportal.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;

/** Each member's daily allowance of assistant questions, counted in the database so it holds across restarts and several servers. */
@Component
public class ChatQuota {

    private final JdbcTemplate jdbc;
    private final int perDay;

    public ChatQuota(JdbcTemplate jdbc, @Value("${app.ai.chat-daily-limit:100}") int perDay) {
        this.jdbc = jdbc;
        this.perDay = perDay;
    }

    public int perDay() {
        return perDay;
    }

    /** Takes one question from today's allowance; false when it is all used. */
    public boolean reserve(Long userId) {
        jdbc.update("INSERT INTO ai_chat_usage (user_id, day, messages) VALUES (?, ?, 0) ON DUPLICATE KEY UPDATE messages = messages", userId, today());
        return jdbc.update("UPDATE ai_chat_usage SET messages = messages + 1 WHERE user_id = ? AND day = ? AND messages < ?",
                userId, today(), perDay) == 1;
    }

    /** A question the AI couldn't answer doesn't use up the allowance. */
    public void release(Long userId) {
        jdbc.update("UPDATE ai_chat_usage SET messages = GREATEST(messages - 1, 0) WHERE user_id = ? AND day = ?", userId, today());
    }

    public int used(Long userId) {
        Integer used = jdbc.query("SELECT messages FROM ai_chat_usage WHERE user_id = ? AND day = ?", rs -> rs.next() ? rs.getInt(1) : 0, userId, today());
        return used == null ? 0 : used;
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }
}
