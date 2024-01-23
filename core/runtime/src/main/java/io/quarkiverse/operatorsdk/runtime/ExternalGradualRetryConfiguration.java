package io.quarkiverse.operatorsdk.runtime;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

/**
 * Configuration for {@link io.javaoperatorsdk.operator.processing.retry.GradualRetry}. Will only apply if the
 * {@link io.javaoperatorsdk.operator.processing.retry.Retry} implementation class is
 * {@link io.javaoperatorsdk.operator.processing.retry.GenericRetry}.
 */
@ConfigGroup
public class ExternalGradualRetryConfiguration {

    /**
     * How many times an operation should be retried before giving up
     */
    @ConfigItem
    public Optional<Integer> maxAttempts;

    /**
     * The configuration of the retry interval.
     */
    @ConfigItem
    public ExternalGradualRetryIntervalConfiguration interval;
}
