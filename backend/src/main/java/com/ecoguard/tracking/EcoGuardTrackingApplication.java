package com.ecoguard.tracking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for EcoGuard Tracking Portal backend.
 * This application provides a RESTful API for tracking stolen devices
 * using the EcoGuard V2 proactive architecture.
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableCaching
@EnableScheduling
public class EcoGuardTrackingApplication {

    public static void main(String[] args) {
        SpringApplication.run(EcoGuardTrackingApplication.class, args);
    }
}
