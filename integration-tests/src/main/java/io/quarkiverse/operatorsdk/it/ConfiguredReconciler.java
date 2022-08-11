package io.quarkiverse.operatorsdk.it;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.retry.GradualRetry;

@ControllerConfiguration(name = ConfiguredReconciler.NAME, namespaces = "foo")
@GradualRetry(maxAttempts = ConfiguredReconciler.MAX_ATTEMPTS)
public class ConfiguredReconciler implements Reconciler<ChildTestResource2> {

    public static final String NAME = "annotation";
    public static final int MAX_ATTEMPTS = 23;

    @Override
    public UpdateControl<ChildTestResource2> reconcile(ChildTestResource2 childTestResource2,
            Context<ChildTestResource2> context) throws Exception {
        return null;
    }
}
