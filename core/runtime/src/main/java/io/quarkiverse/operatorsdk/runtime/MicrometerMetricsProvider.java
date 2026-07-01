package io.quarkiverse.operatorsdk.runtime;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.javaoperatorsdk.operator.monitoring.micrometer.MicrometerMetrics;
import io.javaoperatorsdk.operator.monitoring.micrometer.MicrometerMetricsV2;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.arc.DefaultBean;

@Singleton
public class MicrometerMetricsProvider {

    @Inject
    BuildTimeOperatorConfiguration buildTimeOperatorConfiguration;

    @Inject
    MeterRegistry registry;

    private Metrics metrics;

    @PostConstruct
    void init() {
        metrics = buildTimeOperatorConfiguration.useV1Metrics()
                ? MicrometerMetrics.newPerResourceCollectingMicrometerMetricsBuilder(registry).build()
                : MicrometerMetricsV2.newBuilder(registry).build();
    }

    @Produces
    @DefaultBean
    public Metrics getMetrics() {
        return metrics;
    }
}
