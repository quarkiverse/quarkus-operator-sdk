package io.quarkiverse.operatorsdk.helm.deployment.test.sources;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_CURRENT_NAMESPACE;

import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration(name = WatchCurrentNamespaceReconciler.NAME, informer = @Informer(namespaces = WATCH_CURRENT_NAMESPACE))
public class WatchCurrentNamespaceReconciler implements Reconciler<SimpleCR> {

    public static final String NAME = "watch-current-namespace-reconciler";

    @Override
    public UpdateControl<SimpleCR> reconcile(SimpleCR simpleCR, Context<SimpleCR> context) {
        return null;
    }
}
