package io.quarkiverse.operatorsdk.runtime;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface ExternalRetryConfiguration {

    /**
     * How many times an operation should be retried before giving up
     */
    Optional<Integer> maxAttempts();

    /**
     * The configuration of the retry interval.
     */
    ExternalIntervalConfiguration interval();
}
