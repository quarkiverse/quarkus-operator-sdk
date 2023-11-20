package io.quarkiverse.operatorsdk.it;

import java.time.Duration;
import java.util.Optional;

import io.javaoperatorsdk.operator.api.config.AnnotationConfigurable;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimiter;

@SuppressWarnings("rawtypes")
public class CustomRateLimiter implements RateLimiter, AnnotationConfigurable<CustomRateConfiguration> {
    private int value;

    @Override
    public Optional<Duration> isLimited(RateLimitState rateLimitState) {
        return Optional.empty();
    }

    @Override
    public RateLimitState initState() {
        return null;
    }

    @Override
    public void initFrom(CustomRateConfiguration customRateConfiguration) {
        this.value = customRateConfiguration.value();
    }

    @SuppressWarnings("unused")
    // make it visible for JSON serialization
    public int getValue() {
        return value;
    }
}
