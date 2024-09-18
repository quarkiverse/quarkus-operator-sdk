package io.quarkiverse.operatorsdk.test.sources;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration(name = WatchAllReconciler.NAME, namespaces = Constants.WATCH_ALL_NAMESPACES)
public class WatchAllReconciler implements Reconciler<ConfigMap> {

    public static final String NAME = "watchall";

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap configMap, Context<ConfigMap> context) throws Exception {
        return UpdateControl.noUpdate();
    }
}
