package io.quarkiverse.operatorsdk.it;

import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;

@ControllerConfiguration(name = TestReconciler.NAME)
public class TestReconciler extends AbstractReconciler<Test> {
    public static final String NAME = "test";
}
