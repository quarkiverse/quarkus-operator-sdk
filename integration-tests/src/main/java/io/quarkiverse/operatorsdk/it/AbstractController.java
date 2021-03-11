package io.quarkiverse.operatorsdk.it;

import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.DeleteControl;
import io.javaoperatorsdk.operator.api.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;

public abstract class AbstractController<T extends TestResource> implements
        RegistrableController<T> {

    private boolean initialized;

    @Override
    public void init(EventSourceManager eventSourceManager) {
        initialized = true;
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public DeleteControl deleteResource(T resource, Context<T> context) {
        return null;
    }

    @Override
    public UpdateControl<T> createOrUpdateResource(T resource, Context<T> context) {
        return null;
    }
}
