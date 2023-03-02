package io.quarkiverse.operatorsdk.it;

import jakarta.enterprise.context.ApplicationScoped;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ApplicationScoped
@ControllerConfiguration(name = ApplicationScopedReconciler.NAME)
public class ApplicationScopedReconciler implements Reconciler<ChildTestResource> {

    public static final String NAME = "ApplicationScoped";

    @Override
    public UpdateControl<ChildTestResource> reconcile(ChildTestResource childTestResource,
            Context context) {
        return UpdateControl.noUpdate();
    }
}
