package io.quarkiverse.operatorsdk.it;

import java.util.Collections;
import java.util.Map;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

public abstract class AbstractReconciler<T extends TestResource> implements
        RegistrableReconciler<T>, EventSourceInitializer<T> {

    private boolean initialized;

    @Override
    public UpdateControl<T> reconcile(T t, Context<T> context) {
        return null;
    }

    @Override
    public Map<String, EventSource> prepareEventSources(EventSourceContext<T> eventSourceContext) {
        // this method gets called when the controller gets registered
        initialized = true;
        return Collections.emptyMap();
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }
}
