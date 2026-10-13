package com.example.productionlab.controller;

import com.example.productionlab.config.LabDelayProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Simulates slow responses. The sleep deliberately blocks a Tomcat worker thread,
 * so concurrent calls can be used to reproduce thread-pool saturation.
 */
@RestController
public class LabDelayController {

    private final LabDelayProperties properties;

    public LabDelayController(LabDelayProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/api/v1/lab/delay")
    public Map<String, Long> delay(@RequestParam(defaultValue = "1000") long ms) {
        if (ms < 0 || ms > properties.maxMs()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "ms must be between 0 and " + properties.maxMs());
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Delay interrupted");
        }
        return Map.of("delayedMs", ms);
    }
}
