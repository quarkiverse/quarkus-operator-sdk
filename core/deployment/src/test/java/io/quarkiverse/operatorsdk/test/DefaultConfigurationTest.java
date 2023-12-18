package io.quarkiverse.operatorsdk.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.quarkiverse.operatorsdk.test.sources.*;
import io.quarkus.test.QuarkusUnitTest;

public class DefaultConfigurationTest {

    @RegisterExtension
    static QuarkusUnitTest runner = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.operator-sdk.start-operator", "false")
            .withApplicationRoot(
                    jar -> jar.addClasses(SimpleReconciler.class, SimpleCR.class, SimpleStatus.class, SimpleSpec.class));

    @Inject
    ConfigurationService configurationService;

    @Inject
    SimpleReconciler testReconciler;

    @Test
    void checkDefaultOperatorLevelNamespaces() {
        final var config = configurationService.getConfigurationFor(testReconciler);
        assertEquals(Constants.DEFAULT_NAMESPACES_SET, config.getNamespaces());
    }
}
