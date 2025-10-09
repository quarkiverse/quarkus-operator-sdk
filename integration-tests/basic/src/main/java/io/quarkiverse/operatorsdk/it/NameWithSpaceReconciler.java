package io.quarkiverse.operatorsdk.it;

import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration(name = NameWithSpaceReconciler.NAME)
public class NameWithSpaceReconciler implements Reconciler<ServiceAccount> {

    public static final String NAME = "name with space";

    @Override
    public UpdateControl<ServiceAccount> reconcile(ServiceAccount serviceAccount,
            Context<ServiceAccount> context) throws Exception {
        return null;
    }
}
