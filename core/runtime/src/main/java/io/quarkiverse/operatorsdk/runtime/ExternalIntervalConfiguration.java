package io.quarkiverse.operatorsdk.runtime;

import java.util.Optional;

import io.javaoperatorsdk.operator.api.config.RetryConfiguration;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ExternalIntervalConfiguration {
    String DEFAULT_INITIAL_INTERVAL_AS_STRING = "" + RetryConfiguration.DEFAULT_INITIAL_INTERVAL;
    String DEFAULT_MULTIPLIER_AS_STRING = "" + RetryConfiguration.DEFAULT_MULTIPLIER;

    /**
     * The initial interval that the controller waits for before attempting the first retry
     */
    @WithDefault(DEFAULT_INITIAL_INTERVAL_AS_STRING)
    Long initial();

    /**
     * The value by which the initial interval is multiplied by for each retry
     */
    @WithDefault(DEFAULT_MULTIPLIER_AS_STRING)
    Double multiplier();

    /**
     * The maximum interval that the controller will wait for before attempting a retry, regardless of
     * all other configuration
     */
    Optional<Long> max();
}
