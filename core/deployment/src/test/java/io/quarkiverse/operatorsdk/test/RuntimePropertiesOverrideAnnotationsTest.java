package io.quarkiverse.operatorsdk.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.quarkiverse.operatorsdk.common.ConfigurationUtils;
import io.quarkiverse.operatorsdk.test.sources.*;
import io.quarkus.test.QuarkusUnitTest;

public class RuntimePropertiesOverrideAnnotationsTest {

    @RegisterExtension
    static QuarkusUnitTest runner = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(SameNSReconciler.class, SpecificNSReconciler.class))
            .overrideConfigKey("quarkus.operator-sdk.namespaces", "foo")
            .overrideConfigKey(ConfigurationUtils.getNamespacesPropertyName(SpecificNSReconciler.NAME, false),
                    SpecificNSReconciler.NS);

    @Inject
    ConfigurationService configurationService;

    @Inject
    SameNSReconciler sameNSReconciler;

    @Inject
    SpecificNSReconciler specificNSReconciler;

    @Test
    void runtimeDefaultPropertyShouldOverrideAnnotation() {
        assertEquals(Set.of("foo"), configurationService.getConfigurationFor(sameNSReconciler).getNamespaces());
        assertEquals(Set.of(SpecificNSReconciler.NS),
                configurationService.getConfigurationFor(specificNSReconciler).getNamespaces());
    }
}
