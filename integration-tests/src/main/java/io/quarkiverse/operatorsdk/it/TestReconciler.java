package io.quarkiverse.operatorsdk.it;

import java.util.concurrent.TimeUnit;

import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.ReconciliationMaxInterval;

@ControllerConfiguration(name = TestReconciler.NAME, reconciliationMaxInterval = @ReconciliationMaxInterval(interval = TestReconciler.INTERVAL, timeUnit = TimeUnit.SECONDS))
public class TestReconciler extends AbstractReconciler<Test> {
    public static final String NAME = "test";
    public static final int INTERVAL = 50;
}
