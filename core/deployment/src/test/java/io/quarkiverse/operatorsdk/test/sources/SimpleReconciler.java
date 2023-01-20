package io.quarkiverse.operatorsdk.test.sources;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration(name = SimpleReconciler.NAME)
public class SimpleReconciler implements Reconciler<SimpleCR> {

    public static final String NAME = "simple";

    @Override
    public UpdateControl<SimpleCR> reconcile(SimpleCR simpleCR, Context<SimpleCR> context) {
        return null;
    }
}
