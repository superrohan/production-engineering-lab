package com.example.productionlab;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Explicit lab opt-in: failure simulation enabled and a lower delay ceiling,
 * both supplied the same way the LAB_FAILURE_ENABLED / LAB_DELAY_MAX_MS env vars would.
 */
@SpringBootTest(properties = {"lab.failure.enabled=true", "lab.delay.max-ms=200"})
@AutoConfigureMockMvc
@AutoConfigureObservability
class FailureSimulationEnabledIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void failureEndpointReturns500AndIsCountedAsServerError() throws Exception {
        mockMvc.perform(get("/api/v1/lab/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Simulated failure"));

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(content().string(containsString("outcome=\"SERVER_ERROR\",status=\"500\",uri=\"/api/v1/lab/failure\"")));
    }

    @Test
    void configuredDelayCeilingIsEnforced() throws Exception {
        mockMvc.perform(get("/api/v1/lab/delay").param("ms", "200")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/lab/delay").param("ms", "201"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("ms must be between 0 and 200"));
    }
}
