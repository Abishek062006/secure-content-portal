package com.secureportal.infra;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;

/**
 * Where login sessions live. MySQL by default, so the app needs nothing but its database; with
 * {@code app.redis.enabled=true} they move to Redis, which every app instance behind a load balancer can share and
 * which expires idle sessions on its own instead of being swept out of a table.
 */
@Configuration
public class SessionStoreConfig {

    private static final int IDLE_SECONDS = 1800;

    @Configuration
    @ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
    @EnableRedisHttpSession(maxInactiveIntervalInSeconds = IDLE_SECONDS)
    static class RedisSessions {
    }

    @Configuration
    @ConditionalOnProperty(name = "app.redis.enabled", havingValue = "false", matchIfMissing = true)
    @EnableJdbcHttpSession(maxInactiveIntervalInSeconds = IDLE_SECONDS)
    static class JdbcSessions {
    }
}
