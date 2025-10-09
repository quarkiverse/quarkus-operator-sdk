package io.quarkiverse.operatorsdk.it;

import java.util.concurrent.TimeUnit;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.MaxReconciliationInterval;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration(name = TestReconciler.NAME, maxReconciliationInterval = @MaxReconciliationInterval(interval = TestReconciler.INTERVAL, timeUnit = TimeUnit.SECONDS))
public class TestReconciler implements Reconciler<Test> {
    public static final String NAME = "test";
    public static final int INTERVAL = 50;

    @Override
    public UpdateControl<Test> reconcile(Test test, Context<Test> context) throws Exception {
        return null;
    }
}
