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
            .withApplicationRoot(
                    jar -> jar.addClasses(SimpleReconciler.class, SimpleCR.class, SimpleStatus.class, SimpleSpec.class,
                            SameNSReconciler.class));

    @Inject
    ConfigurationService configurationService;

    @Inject
    SimpleReconciler testReconciler;

    @Inject
    SameNSReconciler sameNSReconciler;

    @Test
    void checkDefaultOperatorLevelNamespaces() {
        final var config = configurationService.getConfigurationFor(testReconciler);
        assertEquals(Constants.DEFAULT_NAMESPACES_SET, config.getNamespaces());

        // build time values are not propagated by default anymore
        assertEquals(Constants.DEFAULT_NAMESPACES_SET,
                configurationService.getConfigurationFor(sameNSReconciler).getNamespaces());
    }
}
