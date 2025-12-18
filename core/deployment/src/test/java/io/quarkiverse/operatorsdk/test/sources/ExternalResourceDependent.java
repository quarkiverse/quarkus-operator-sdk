package io.quarkiverse.operatorsdk.test.sources;

import java.util.Set;

import io.javaoperatorsdk.operator.processing.dependent.external.PerResourcePollingDependentResource;

public class ExternalResourceDependent extends PerResourcePollingDependentResource<Void, External, String> {
    @Override
    public Set<Void> fetchResources(External externalV1) {
        return Set.of();
    }
}
