package io.quarkiverse.operatorsdk.bundle.sources;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.quarkiverse.operatorsdk.annotations.CSVMetadata;

@CSVMetadata(name = "second-operator")
public class SecondReconciler implements Reconciler<Second> {

    @Override
    public UpdateControl<Second> reconcile(Second request, Context<Second> context) {
        return UpdateControl.noUpdate();
    }
}