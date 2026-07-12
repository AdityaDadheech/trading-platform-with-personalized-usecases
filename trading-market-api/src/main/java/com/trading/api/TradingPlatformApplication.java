package com.trading.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Trading Platform backend.
 *
 * Component scan covers:
 *   com.trading.api.*        — this module (controllers, services, config)
 *   com.trading.kite.*       — trading-kite-client module (auto-picked up)
 *   com.trading.common.*     — trading-common module (auto-picked up)
 *
 * Run with:
 *   mvn spring-boot:run -Dspring-boot.run.profiles=dev
 *
 * Or in IntelliJ:
 *   Edit Run Configuration → Active profiles → dev
 */
@SpringBootApplication(scanBasePackages = {
    "com.trading.api",
    "com.trading.kite",
    "com.trading.common",
    "com.trading.ingestion",
    "com.trading.aggregator"
})
@EnableRetry
@EnableScheduling
@EnableConfigurationProperties
public class TradingPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradingPlatformApplication.class, args);
    }
}
