package io.quarkiverse.operatorsdk.test.reconcilers;

import io.fabric8.istio.api.networking.v1beta1.Gateway;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

public class GatewayReconciler implements Reconciler<Gateway> {

    @Override
    public UpdateControl<Gateway> reconcile(Gateway gateway, Context context) {
        return UpdateControl.noUpdate();
    }
}
