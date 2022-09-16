package io.quarkiverse.operatorsdk.it;

import static io.quarkiverse.operatorsdk.it.EmptyReconciler.NAME;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration(name = NAME)
public class EmptyReconciler implements Reconciler<EmptyCR> {

    public static final String NAME = "empty";

    @Override
    public UpdateControl<EmptyCR> reconcile(EmptyCR emptyCR, Context<EmptyCR> context)
            throws Exception {
        return UpdateControl.noUpdate();
    }
}
