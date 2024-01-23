package io.quarkiverse.operatorsdk.runtime;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

@ConfigGroup
public class ExternalGradualRetryIntervalConfiguration {
    public static final long UNSET_INITIAL = -1;
    public static final double UNSET_MULTIPLIER = -1;
    public static final long UNSET_MAX = -1;

    /**
     * The initial interval that the controller waits for before attempting the first retry
     */
    @ConfigItem
    public Optional<Long> initial;

    /**
     * The value by which the initial interval is multiplied by for each retry
     */
    @ConfigItem
    public Optional<Double> multiplier;

    /**
     * The maximum interval that the controller will wait for before attempting a retry, regardless of
     * all other configuration
     */
    @ConfigItem
    public Optional<Long> max;
}
