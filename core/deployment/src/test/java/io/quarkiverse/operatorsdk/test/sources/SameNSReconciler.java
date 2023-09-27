package io.quarkiverse.operatorsdk.test.sources;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.*;

@ControllerConfiguration(namespaces = Constants.WATCH_CURRENT_NAMESPACE)
public class SameNSReconciler implements Reconciler<ConfigMap> {
    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap configMap, Context<ConfigMap> context) throws Exception {
        return null;
    }
}
