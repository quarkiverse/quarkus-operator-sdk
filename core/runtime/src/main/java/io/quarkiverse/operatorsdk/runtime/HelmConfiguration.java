package io.quarkiverse.operatorsdk.runtime;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface HelmConfiguration {

    /**
     * Can be used to disable helm chart generation.
     */
    @WithDefault("false")
    boolean enabled();

}
