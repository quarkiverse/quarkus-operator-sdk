package io.quarkiverse.operatorsdk.it;

import javax.inject.Singleton;

import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

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
