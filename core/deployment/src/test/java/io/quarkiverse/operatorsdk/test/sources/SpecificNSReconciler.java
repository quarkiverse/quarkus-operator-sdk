package io.quarkiverse.operatorsdk.test.sources;

import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration(name = SpecificNSReconciler.NAME, namespaces = "buildtime-ns")
public class SpecificNSReconciler implements Reconciler<Secret> {

    public static final String NS = "runtime-ns";
    public static final String NAME = "specific";

    @Override
    public UpdateControl<Secret> reconcile(Secret secret, Context<Secret> context) throws Exception {
        return null;
    }
}
