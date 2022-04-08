package io.quarkiverse.operatorsdk.test.reconcilers;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration(name = TestReconciler.NAME)
public class TestReconciler implements Reconciler<Test> {
    public static final String NAME = "test";

    @Override
    public UpdateControl<Test> reconcile(Test test, Context<Test> context) {
        return null;
    }
}
