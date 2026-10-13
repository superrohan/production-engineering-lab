package com.example.productionlab.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Upper bound for the latency simulation endpoint. Validated at startup so that a
 * misconfiguration fails fast (visible as a crash loop) instead of at request time.
 */
@ConfigurationProperties("lab.delay")
public record LabDelayProperties(long maxMs) {

    public static final long HARD_LIMIT_MS = 30_000;

    public LabDelayProperties {
        if (maxMs < 0 || maxMs > HARD_LIMIT_MS) {
            throw new IllegalArgumentException(
                    "lab.delay.max-ms must be between 0 and " + HARD_LIMIT_MS + " but was " + maxMs);
        }
    }
}
