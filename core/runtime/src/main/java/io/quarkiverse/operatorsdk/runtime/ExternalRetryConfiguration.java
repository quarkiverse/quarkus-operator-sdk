package io.quarkiverse.operatorsdk.runtime;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

@ConfigGroup
public class ExternalRetryConfiguration {

    /**
     * How many times an operation should be retried before giving up
     */
    @ConfigItem
    public Optional<Integer> maxAttempts;

    /**
     * The configuration of the retry interval.
     */
    @ConfigItem
    public ExternalIntervalConfiguration interval;
}
