package io.quarkiverse.operatorsdk.it;

import java.util.Collections;
import java.util.List;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

public abstract class AbstractReconciler<T extends TestResource> implements
        RegistrableReconciler<T>, EventSourceInitializer<T> {

    private boolean initialized;

    @Override
    public UpdateControl<T> reconcile(T t, Context context) {
        return null;
    }

    @Override
    public DeleteControl cleanup(T resource, Context context) {
        return RegistrableReconciler.super.cleanup(resource, context);
    }

    @Override
    public List<EventSource> prepareEventSources(EventSourceContext<T> eventSourceContext) {
        // this method gets called when the controller gets registered
        initialized = true;
        return Collections.emptyList();
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }
}
