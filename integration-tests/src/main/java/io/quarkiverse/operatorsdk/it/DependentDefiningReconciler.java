package io.quarkiverse.operatorsdk.it;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

// Note that this reconciler implementation and its dependents are not meant to be realistic but
// rather exercise some of the features
@ControllerConfiguration(name = DependentDefiningReconciler.NAME, dependents = {
        @Dependent(type = ReadOnlyDependentResource.class, name = "read-only"),
        @Dependent(type = CRUDDependentResource.class, name = "crud", dependsOn = "read-only")
})
public class DependentDefiningReconciler implements Reconciler<ConfigMap> {

    public static final String NAME = "dependent";

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap configMap, Context context) {
        return UpdateControl.noUpdate();
    }
}
