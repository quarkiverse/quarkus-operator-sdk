package io.quarkiverse.operatorsdk.helm.deployment.test.sources;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration(name = SpacedReconciler.NAME)
public class SpacedReconciler implements Reconciler<SimpleCR> {

    public static final String NAME = "name with spaces";

    @Override
    public UpdateControl<SimpleCR> reconcile(SimpleCR simpleCR, Context<SimpleCR> context) {
        return null;
    }
}
