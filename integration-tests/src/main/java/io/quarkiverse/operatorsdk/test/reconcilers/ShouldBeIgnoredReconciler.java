package io.quarkiverse.operatorsdk.test.reconcilers;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

public class ShouldBeIgnoredReconciler implements Reconciler<CustomResource<Void, Void>> {

    @Override
    public UpdateControl<CustomResource<Void, Void>> reconcile(CustomResource<Void, Void> resource,
            Context<CustomResource<Void, Void>> context) {
        return UpdateControl.noUpdate();
    }
}
