package com.example.productionlab.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Writes one access-log line per request with method, path, status and duration.
 * <p>
 * Only the path is logged: query strings, headers and bodies may contain sensitive data.
 * Actuator requests (probes, Prometheus scrapes) are skipped because they arrive every
 * few seconds per pod and would drown out real traffic.
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        boolean failed = false;
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            failed = true;
            throw e;
        } finally {
            // An exception escaping the chain becomes a 500, but the status is not set yet.
            int status = failed ? 500 : response.getStatus();
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            log.atLevel(status >= 500 ? Level.WARN : Level.INFO)
                    .addKeyValue("method", request.getMethod())
                    .addKeyValue("path", request.getRequestURI())
                    .addKeyValue("status", status)
                    .addKeyValue("durationMs", durationMs)
                    .log("{} {} {} {}ms", request.getMethod(), request.getRequestURI(), status, durationMs);
        }
    }
}
