package io.quarkiverse.operatorsdk.bundle.sources;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.quarkiverse.operatorsdk.annotations.CSVMetadata;
import io.quarkiverse.operatorsdk.annotations.SharedCSVMetadata;

@CSVMetadata(name = AReconciler.SHARED, version = AReconciler.SHARED_VERSION)
public class AReconciler implements Reconciler<ConfigMap>, SharedCSVMetadata {

    public static final String SHARED_VERSION = "0.0.1";
    public static final String SHARED = "shared";

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap configMap, Context<ConfigMap> context) {
        return null;
    }
}
