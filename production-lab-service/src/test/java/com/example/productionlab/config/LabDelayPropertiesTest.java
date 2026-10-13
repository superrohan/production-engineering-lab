package com.example.productionlab.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class LabDelayPropertiesTest {

    @Test
    void acceptsValuesWithinHardLimit() {
        assertThat(new LabDelayProperties(0).maxMs()).isZero();
        assertThat(new LabDelayProperties(LabDelayProperties.HARD_LIMIT_MS).maxMs())
                .isEqualTo(LabDelayProperties.HARD_LIMIT_MS);
    }

    @Test
    void rejectsNegativeMaximum() {
        assertThatIllegalArgumentException().isThrownBy(() -> new LabDelayProperties(-1));
    }

    @Test
    void rejectsMaximumAboveHardLimit() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LabDelayProperties(LabDelayProperties.HARD_LIMIT_MS + 1));
    }
}
