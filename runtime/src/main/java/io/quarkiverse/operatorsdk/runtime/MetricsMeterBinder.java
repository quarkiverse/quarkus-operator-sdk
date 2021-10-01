package io.quarkiverse.operatorsdk.runtime;

import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

import io.javaoperatorsdk.operator.Metrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

@Singleton
public class MetricsMeterBinder implements MeterBinder {
    private Metrics metrics = Metrics.NOOP;

    @Override
    public void bindTo(MeterRegistry registry) {
        metrics = new Metrics(registry);
    }

    @Produces
    public Metrics getMetrics() {
        return metrics;
    }
}
