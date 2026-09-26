package com.secureportal;

import com.secureportal.ai.AiProperties;
import com.secureportal.config.AppProperties;
import com.secureportal.config.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableConfigurationProperties({AppProperties.class, StorageProperties.class, AiProperties.class,
        com.secureportal.video.TranscodeProperties.class})
public class SecurePortalApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecurePortalApplication.class, args);
    }

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}
