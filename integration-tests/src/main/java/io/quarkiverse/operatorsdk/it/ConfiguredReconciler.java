package io.quarkiverse.operatorsdk.it;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration(name = ConfiguredReconciler.NAME, namespaces = "foo")
public class ConfiguredReconciler implements Reconciler<ChildTestResource2> {

    public static final String NAME = "annotation";

    @Override
    public UpdateControl<ChildTestResource2> reconcile(ChildTestResource2 childTestResource2,
            Context<ChildTestResource2> context) throws Exception {
        return null;
    }
}
