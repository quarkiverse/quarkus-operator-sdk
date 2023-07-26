package io.quarkiverse.operatorsdk.runtime;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

@ConfigGroup
public class HelmConfiguration {

    /**
     * Can be used to disable helm chart generation.
     */
    @ConfigItem(defaultValue = "true")
    public Boolean enabled;

}
