package io.quarkiverse.operatorsdk.test.sources;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_ALL_NAMESPACES;

import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration(name = WatchAllNamespacesReconciler.NAME, informer = @Informer(namespaces = WATCH_ALL_NAMESPACES))
public class WatchAllNamespacesReconciler implements Reconciler<SimpleCR> {

    public static final String NAME = "all-namespaces-reconciler";

    @Override
    public UpdateControl<SimpleCR> reconcile(SimpleCR simpleCR, Context<SimpleCR> context) {
        return null;
    }
}
