package io.quarkiverse.operatorsdk.bundle.sources;

import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

public class CReconciler implements Reconciler<ServiceAccount> {

    @Override
    public UpdateControl<ServiceAccount> reconcile(ServiceAccount serviceAccount,
            Context<ServiceAccount> context) throws Exception {
        return null;
    }
}
