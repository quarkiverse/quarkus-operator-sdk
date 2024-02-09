package io.quarkiverse.operatorsdk.bundle.deployment;

import java.util.Map;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.SmallRyeConfigBuilderCustomizer;

/**
 * Overrides the default value for the Kubernetes extension-provided {@code add-version-to-label-selectors} when the bundle
 * generator is used. See <a href="https://github.com/quarkiverse/quarkus-operator-sdk/issues/823">this issue</a> for more
 * details.
 */
public class KubernetesLabelConfigOverrider implements SmallRyeConfigBuilderCustomizer {
    @Override
    public void configBuilder(SmallRyeConfigBuilder builder) {
        // not sure what value for the priority should be used since they seem pretty random
        builder.withSources(new PropertiesConfigSource(Map.of("quarkus.kubernetes.add-version-to-label-selectors", "false"),
                "KubernetesLabelConfigOverrider", 50));
    }
}
