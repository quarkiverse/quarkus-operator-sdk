package io.quarkiverse.operatorsdk;

import io.fabric8.kubernetes.api.model.Pod;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration(name = ExternalCRDReconciler.NAME)
public class ExternalCRDReconciler implements Reconciler<Pod> {

    public static final String NAME = "externalcrd";

    @Override
    public UpdateControl<Pod> reconcile(Pod pod, Context<Pod> context) {
        return null;
    }
}
