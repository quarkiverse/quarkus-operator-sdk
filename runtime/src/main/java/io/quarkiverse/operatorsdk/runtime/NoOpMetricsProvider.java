package io.quarkiverse.operatorsdk.runtime;

import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

import io.javaoperatorsdk.operator.Metrics;

@Singleton
public class NoOpMetricsProvider {
    @Produces
    public Metrics getMetrics() {
        return Metrics.NOOP;
    }
}
