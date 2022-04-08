package io.quarkiverse.operatorsdk.test.reconcilers;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@ControllerConfiguration(name = DependentDefiningReconciler.NAME, dependents = {
        @Dependent(type = ReadOnlyDependentResource.class),
        @Dependent(type = CRUDDependentResource.class)
})
public class DependentDefiningReconciler implements Reconciler<ConfigMap> {

    public static final String NAME = "dependent";

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap configMap, Context context) {
        return UpdateControl.noUpdate();
    }
}
