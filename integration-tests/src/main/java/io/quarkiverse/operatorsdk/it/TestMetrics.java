package io.quarkiverse.operatorsdk.it;

import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import jakarta.inject.Singleton;

@Singleton
public class TestMetrics implements Metrics, MeterBinder {
    private boolean registryBound;

    @Override
    public void bindTo(MeterRegistry meterRegistry) {
        registryBound = true;
    }

    public boolean isRegistryBound() {
        return registryBound;
    }
}
