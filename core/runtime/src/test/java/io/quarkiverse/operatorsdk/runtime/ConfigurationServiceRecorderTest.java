package io.quarkiverse.operatorsdk.runtime;

import static io.quarkiverse.operatorsdk.runtime.ConfigurationServiceRecorder.shouldStartOperator;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.quarkus.runtime.LaunchMode;

class ConfigurationServiceRecorderTest {

    @Test
    void checkShouldStartOperator() {
        // should start the operator in the absence of configuration if not in test mode
        assertFalse(shouldStartOperator(Optional.empty(), LaunchMode.TEST));
        assertTrue(shouldStartOperator(Optional.empty(), LaunchMode.DEVELOPMENT));
        assertTrue(shouldStartOperator(Optional.empty(), LaunchMode.NORMAL));

        // explicit configuration should have priority
        assertFalse(shouldStartOperator(Optional.of(false), LaunchMode.TEST));
        assertTrue(shouldStartOperator(Optional.of(true), LaunchMode.TEST));
        assertTrue(shouldStartOperator(Optional.of(true), LaunchMode.NORMAL));
        assertFalse(shouldStartOperator(Optional.of(false), LaunchMode.NORMAL));

        assertTrue(shouldStartOperator(null, null));
    }
}