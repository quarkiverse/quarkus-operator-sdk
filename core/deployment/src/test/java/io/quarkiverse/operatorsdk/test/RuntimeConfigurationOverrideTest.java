package io.quarkiverse.operatorsdk.test;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.javaoperatorsdk.operator.Operator;
import io.quarkiverse.operatorsdk.test.sources.ConfiguredReconciler;
import io.quarkiverse.operatorsdk.test.sources.TestCR;
import io.quarkus.test.QuarkusUnitTest;

public class RuntimeConfigurationOverrideTest {

    @RegisterExtension
    static QuarkusUnitTest runner = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.operator-sdk.start-operator", "false")
            .withApplicationRoot(
                    jar -> jar.addClasses(ConfiguredReconciler.class, TestCR.class));

    @Inject
    Operator operator;

    @Inject
    ConfiguredReconciler configuredReconciler;

    @Test
    void checkDefaultOperatorLevelNamespaces() {
        final var config = operator.getConfigurationService().getConfigurationFor(configuredReconciler);
        // configuration service is not updated when a controller is registered with a different configuration
        assertNotEquals(ConfiguredReconciler.LABEL_SELECTOR, config.getInformerConfig().getLabelSelector());

        // up-to-date configuration currently needs to be accessed via RuntimeInfo
        final var registered = operator.getRuntimeInfo().getRegisteredControllers().stream()
                .filter(rc -> config.getName().equals(rc.getConfiguration().getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(registered);
        assertEquals(ConfiguredReconciler.LABEL_SELECTOR, registered.getConfiguration().getInformerConfig().getLabelSelector());
    }
}
