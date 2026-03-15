package io.quarkiverse.operatorsdk.runtime;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.javaoperatorsdk.operator.monitoring.micrometer.MicrometerMetricsV2;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.quarkus.arc.DefaultBean;

@Singleton
public class MicrometerMetricsProvider implements MeterBinder {
    private Metrics metrics = Metrics.NOOP;

    @Override
    public void bindTo(MeterRegistry registry) {
        metrics = MicrometerMetricsV2.newBuilder(registry).build();
    }

    @Produces
    @DefaultBean
    public Metrics getMetrics() {
        return metrics;
    }
}
