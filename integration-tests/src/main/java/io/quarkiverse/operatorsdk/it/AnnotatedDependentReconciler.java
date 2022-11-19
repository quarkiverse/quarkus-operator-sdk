package io.quarkiverse.operatorsdk.it;

import static io.quarkiverse.operatorsdk.it.AnnotatedDependentReconciler.NAME;

import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@ControllerConfiguration(name = NAME, dependents = @Dependent(type = AnnotatedDependentResource.class))
public class AnnotatedDependentReconciler implements Reconciler<Service> {

    public static final String NAME = "annotated-dependent";

    @Override
    public UpdateControl<Service> reconcile(Service service, Context<Service> context)
            throws Exception {
        return null;
    }
}
