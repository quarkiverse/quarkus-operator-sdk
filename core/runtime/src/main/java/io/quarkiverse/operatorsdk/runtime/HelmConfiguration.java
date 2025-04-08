package io.quarkiverse.operatorsdk.runtime;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface HelmConfiguration {

    /**
     * Can be used to disable Helm chart generation.
     */
    @WithDefault("false")
    Boolean enabled();

    /**
     * Specifies the name of the generated Helm chart. This will generate the Helm chart in {@code target/helm/<name>} instead
     * of {@code target/helm} directly, if specified.
     */
    Optional<String> name();
}
