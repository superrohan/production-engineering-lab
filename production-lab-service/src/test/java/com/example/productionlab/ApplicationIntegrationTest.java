package com.example.productionlab;

import com.example.productionlab.controller.LabFailureController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full application context with default configuration. MockMvc runs the complete
 * filter chain (including metrics and request logging) synchronously.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability // metrics export is disabled in tests unless requested
class ApplicationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext context;

    @Test
    void listsOrders() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value("1001"));
    }

    @Test
    void returnsOrderById() throws Exception {
        mockMvc.perform(get("/api/v1/orders/1002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1002"))
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void returnsProblemDetail404ForUnknownOrder() throws Exception {
        mockMvc.perform(get("/api/v1/orders/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Order not found"));
    }

    @Test
    void healthEndpointsAreUp() throws Exception {
        for (String path : new String[]{"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));
        }
    }

    @Test
    void healthDetailsAreHiddenByDefault() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void onlyHealthAndPrometheusActuatorEndpointsAreExposed() throws Exception {
        mockMvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/beans")).andExpect(status().isNotFound());
    }

    @Test
    void prometheusExposesHttpAndJvmMetricsWithBoundedLabels() throws Exception {
        mockMvc.perform(get("/api/v1/orders/1001")).andExpect(status().isOk());

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("uri=\"/api/v1/orders/{id}\"")))
                .andExpect(content().string(not(containsString("uri=\"/api/v1/orders/1001\""))))
                .andExpect(content().string(containsString("http_server_requests_seconds_bucket")))
                .andExpect(content().string(containsString("application=\"production-lab-service\"")))
                .andExpect(content().string(containsString("jvm_memory_used_bytes")))
                .andExpect(content().string(containsString("process_cpu_usage")));
    }

    @Test
    void delayReturnsAfterRequestedDuration() throws Exception {
        long start = System.nanoTime();
        mockMvc.perform(get("/api/v1/lab/delay").param("ms", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delayedMs").value(100));
        assertThat((System.nanoTime() - start) / 1_000_000).isGreaterThanOrEqualTo(100);
    }

    @Test
    void delayRejectsOutOfRangeAndInvalidValues() throws Exception {
        mockMvc.perform(get("/api/v1/lab/delay").param("ms", "-1")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/lab/delay").param("ms", "5001")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/lab/delay").param("ms", "abc")).andExpect(status().isBadRequest());
    }

    @Test
    void failureSimulationIsDisabledByDefault() throws Exception {
        assertThat(context.getBeansOfType(LabFailureController.class)).isEmpty();
        mockMvc.perform(get("/api/v1/lab/failure")).andExpect(status().isNotFound());
    }
}
