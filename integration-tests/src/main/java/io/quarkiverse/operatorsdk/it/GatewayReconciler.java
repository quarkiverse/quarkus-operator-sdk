package io.quarkiverse.operatorsdk.it;

import io.fabric8.kubernetes.api.model.gatewayapi.v1.Gateway;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

public class GatewayReconciler implements Reconciler<Gateway> {

    @Override
    public UpdateControl<Gateway> reconcile(Gateway gateway, Context context) {
        return UpdateControl.noUpdate();
    }
}
