package io.quarkiverse.operatorsdk.test.sources;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@Workflow(dependents = {
        @Dependent(type = ExternalResourceDependent.class)
})
@ControllerConfiguration(name = ResourceIDReconciler.NAME)
public class ResourceIDReconciler implements Reconciler<External> {

    public static final String NAME = "resource-id-reconciler";

    @Override
    public UpdateControl<External> reconcile(External externalV1, Context<External> context) {
        return UpdateControl.noUpdate();
    }
}
