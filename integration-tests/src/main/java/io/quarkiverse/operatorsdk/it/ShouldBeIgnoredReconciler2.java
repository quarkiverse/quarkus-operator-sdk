package io.quarkiverse.operatorsdk.it;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

public class ShouldBeIgnoredReconciler2 implements Reconciler<HasMetadata> {

    @Override
    public UpdateControl<HasMetadata> reconcile(HasMetadata hasMetadata, Context context) {
        return UpdateControl.noUpdate();
    }
}
