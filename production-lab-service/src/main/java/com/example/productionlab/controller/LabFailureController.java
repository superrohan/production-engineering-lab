package com.example.productionlab.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Returns HTTP 500 on every call, for testing error-rate alerts.
 * <p>
 * The bean is only created when {@code lab.failure.enabled=true}. Otherwise the route
 * does not exist and callers get a plain 404, which reveals nothing about the endpoint.
 */
@RestController
@ConditionalOnProperty(name = "lab.failure.enabled", havingValue = "true")
public class LabFailureController {

    private static final Logger log = LoggerFactory.getLogger(LabFailureController.class);

    public LabFailureController() {
        log.warn("Failure simulation endpoint is ENABLED (lab.failure.enabled=true). Never enable this in production.");
    }

    @GetMapping("/api/v1/lab/failure")
    public void fail() {
        log.error("Simulated failure triggered");
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Simulated failure");
    }
}
