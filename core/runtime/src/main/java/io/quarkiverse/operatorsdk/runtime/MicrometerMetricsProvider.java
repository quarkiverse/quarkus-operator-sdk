package io.quarkiverse.operatorsdk.runtime;

import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.javaoperatorsdk.operator.monitoring.micrometer.MicrometerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@Singleton
public class MicrometerMetricsProvider implements MeterBinder {
    private Metrics metrics = Metrics.NOOP;

    @Override
    public void bindTo(MeterRegistry registry) {
        metrics = new MicrometerMetrics(registry);
    }

    @Produces
    @DefaultBean
    public Metrics getMetrics() {
        return metrics;
    }
}
