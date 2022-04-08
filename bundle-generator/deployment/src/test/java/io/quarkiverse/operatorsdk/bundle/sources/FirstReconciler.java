package io.quarkiverse.operatorsdk.bundle.sources;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.quarkiverse.operatorsdk.bundle.runtime.CSVMetadata;

@CSVMetadata(name = "first-operator")
public class FirstReconciler implements Reconciler<First> {

    @Override
    public UpdateControl<First> reconcile(First request, Context<First> context) {
        return UpdateControl.noUpdate();
    }
}