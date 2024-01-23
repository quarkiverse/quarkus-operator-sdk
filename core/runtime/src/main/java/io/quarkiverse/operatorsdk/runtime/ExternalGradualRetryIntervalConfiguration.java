package io.quarkiverse.operatorsdk.runtime;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface ExternalGradualRetryIntervalConfiguration {
    long UNSET_INITIAL = -1;
    double UNSET_MULTIPLIER = -1;
    long UNSET_MAX = -1;

    /**
     * The initial interval that the controller waits for before attempting the first retry
     */
    Optional<Long> initial();

    /**
     * The value by which the initial interval is multiplied by for each retry
     */
    Optional<Double> multiplier();

    /**
     * The maximum interval that the controller will wait for before attempting a retry, regardless of
     * all other configuration
     */
    Optional<Long> max();
}
