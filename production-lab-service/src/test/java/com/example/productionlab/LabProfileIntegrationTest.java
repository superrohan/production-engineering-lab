package com.example.productionlab;

import com.example.productionlab.config.LabDelayProperties;
import com.example.productionlab.controller.LabFailureController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The lab profile must start cleanly and must NOT enable failure simulation on its own.
 */
@SpringBootTest
@ActiveProfiles("lab")
class LabProfileIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void labProfileKeepsFailureSimulationDisabled() {
        assertThat(context.getBeansOfType(LabFailureController.class)).isEmpty();
    }

    @Test
    void labProfileUsesDefaultDelayCeiling() {
        assertThat(context.getBean(LabDelayProperties.class).maxMs()).isEqualTo(5000);
    }
}
