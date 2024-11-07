package io.quarkiverse.operatorsdk.bundle.sources;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.quarkiverse.operatorsdk.annotations.CSVMetadata;

@CSVMetadata(bundleName = "first-operator", version = FirstReconciler.VERSION, installModes = @CSVMetadata.InstallMode(type = "MultiNamespace", supported = false))
public class FirstReconciler implements Reconciler<First> {

    public static final String VERSION = "first-version";
    public static final String REPLACES = "first-replaces";

    @Override
    public UpdateControl<First> reconcile(First request, Context<First> context) {
        return UpdateControl.noUpdate();
    }
}