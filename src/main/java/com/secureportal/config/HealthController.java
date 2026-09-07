package com.secureportal.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A minimal, public liveness check for Render's health check and for
 * confirming a cold-started instance is actually up. Deliberately not
 * Spring Boot Actuator — that's a heavier dependency than a one-line
 * "is the process alive and serving requests" check needs.
 */
@RestController
public class HealthController {

    @GetMapping("/healthz")
    public String healthz() {
        return "OK";
    }
}
