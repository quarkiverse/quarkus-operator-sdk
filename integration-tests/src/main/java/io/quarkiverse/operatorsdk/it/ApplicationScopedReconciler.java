package io.quarkiverse.operatorsdk.it;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import jakarta.enterprise.context.ApplicationScoped;

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
