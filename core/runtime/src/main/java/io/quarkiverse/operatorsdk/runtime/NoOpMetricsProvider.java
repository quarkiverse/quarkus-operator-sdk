package io.quarkiverse.operatorsdk.runtime;

import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.quarkus.arc.DefaultBean;

@Singleton
public class NoOpMetricsProvider {
    @Produces
    @DefaultBean
    public Metrics getMetrics() {
        return Metrics.NOOP;
    }
}
