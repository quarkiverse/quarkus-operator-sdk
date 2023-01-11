package io.quarkiverse.operatorsdk.runtime;

import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@Singleton
public class NoOpMetricsProvider {
    @Produces
    @DefaultBean
    public Metrics getMetrics() {
        return Metrics.NOOP;
    }
}
