package io.quarkiverse.operatorsdk.runtime;

import java.util.Optional;

import io.javaoperatorsdk.operator.api.config.RetryConfiguration;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

@ConfigGroup
public class ExternalIntervalConfiguration {

    /**
     * The initial interval that the controller waits for before attempting the first retry
     */
    @ConfigItem(defaultValue = "" + RetryConfiguration.DEFAULT_INITIAL_INTERVAL)
    public Long initial;

    /**
     * The value by which the initial interval is multiplied by for each retry
     */
    @ConfigItem(defaultValue = "" + RetryConfiguration.DEFAULT_MULTIPLIER)
    public Double multiplier;

    /**
     * The maximum interval that the controller will wait for before attempting a retry, regardless of
     * all other configuration
     */
    @ConfigItem
    public Optional<Long> max;
}
