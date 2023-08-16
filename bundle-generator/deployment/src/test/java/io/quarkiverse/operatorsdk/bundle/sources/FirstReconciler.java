package io.quarkiverse.operatorsdk.bundle.sources;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.quarkiverse.operatorsdk.bundle.runtime.CSVMetadata;

@CSVMetadata(name = "first-operator", version = FirstReconciler.VERSION)
public class FirstReconciler implements Reconciler<First> {

    public static final String VERSION = "first-version";

    @Override
    public UpdateControl<First> reconcile(First request, Context<First> context) {
        return UpdateControl.noUpdate();
    }
}