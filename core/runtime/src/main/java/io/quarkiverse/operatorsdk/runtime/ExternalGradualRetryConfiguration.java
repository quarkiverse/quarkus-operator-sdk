package io.quarkiverse.operatorsdk.runtime;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;

/**
 * Configuration for {@link io.javaoperatorsdk.operator.processing.retry.GradualRetry}. Will only apply if the
 * {@link io.javaoperatorsdk.operator.processing.retry.Retry} implementation class is
 * {@link io.javaoperatorsdk.operator.processing.retry.GenericRetry}.
 */
@ConfigGroup
public interface ExternalGradualRetryConfiguration {

    /**
     * How many times an operation should be retried before giving up
     */
    Optional<Integer> maxAttempts();

    /**
     * The configuration of the retry interval.
     */
    ExternalGradualRetryIntervalConfiguration interval();
}
