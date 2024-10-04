package io.quarkiverse.operatorsdk.test.sources;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

public class SimpleReconcilerV2 implements Reconciler<SimpleCRV2> {
    @Override
    public UpdateControl<SimpleCRV2> reconcile(SimpleCRV2 simpleCRV2, Context<SimpleCRV2> context) throws Exception {
        return UpdateControl.noUpdate();
    }
}
