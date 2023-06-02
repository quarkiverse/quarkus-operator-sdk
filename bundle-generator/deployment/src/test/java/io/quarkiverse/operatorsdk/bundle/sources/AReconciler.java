package io.quarkiverse.operatorsdk.bundle.sources;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.quarkiverse.operatorsdk.bundle.runtime.CSVMetadata;
import io.quarkiverse.operatorsdk.bundle.runtime.SharedCSVMetadata;

@CSVMetadata(name = "shared", version = "0.0.1")
public class AReconciler implements Reconciler<ConfigMap>, SharedCSVMetadata {

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap configMap, Context<ConfigMap> context)
            throws Exception {
        return null;
    }
}
