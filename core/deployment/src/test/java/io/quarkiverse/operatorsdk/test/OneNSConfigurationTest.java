package io.quarkiverse.operatorsdk.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.quarkiverse.operatorsdk.test.sources.OneNSReconciler;
import io.quarkiverse.operatorsdk.test.sources.TestCR;
import io.quarkus.test.QuarkusExtensionTest;

public class OneNSConfigurationTest {

    @RegisterExtension
    static QuarkusExtensionTest runner = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.operator-sdk.start-operator", "false")
            .overrideConfigKey("quarkus.http.test-port", "0")
            .withApplicationRoot(
                    jar -> jar.addClasses(OneNSReconciler.class, TestCR.class));

    @Inject
    ConfigurationService configurationService;

    @Inject
    OneNSReconciler testReconciler;

    @Test
    void checkDefaultOperatorLevelNamespaces() {
        final var config = configurationService.getConfigurationFor(testReconciler);
        assertEquals(Set.of(OneNSReconciler.NS), config.getInformerConfig().getNamespaces());
    }
}
