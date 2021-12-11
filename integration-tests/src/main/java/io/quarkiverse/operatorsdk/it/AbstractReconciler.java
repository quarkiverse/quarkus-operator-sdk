package io.quarkiverse.operatorsdk.it;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.source.EventSourceRegistry;

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
    public void prepareEventSources(EventSourceRegistry<T> eventSourceRegistry) {
        // this method gets called when the controller gets registered
        initialized = true;
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }
}
