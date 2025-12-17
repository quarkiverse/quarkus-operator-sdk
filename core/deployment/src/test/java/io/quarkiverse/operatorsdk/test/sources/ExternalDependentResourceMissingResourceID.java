package io.quarkiverse.operatorsdk.test.sources;

import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.dependent.AbstractExternalDependentResource;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

public class ExternalDependentResourceMissingResourceID extends AbstractExternalDependentResource {
    @Override
    protected EventSource createEventSource(EventSourceContext eventSourceContext) {
        return null;
    }
}
