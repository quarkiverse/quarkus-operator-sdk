package io.quarkiverse.operatorsdk.test.sources;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration(name = UnownedReconciler.NAME)
public class UnownedReconciler implements Reconciler<TestCR> {

    public static final String NAME = "unowned";

    @Override
    public UpdateControl<TestCR> reconcile(TestCR testCR, Context<TestCR> context) throws Exception {
        return null;
    }
}
