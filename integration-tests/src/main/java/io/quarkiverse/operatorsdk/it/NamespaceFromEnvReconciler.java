package io.quarkiverse.operatorsdk.it;

import io.fabric8.kubernetes.api.model.Pod;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration(name = NamespaceFromEnvReconciler.NAME, namespaces = { "static",
        "${" + NamespaceFromEnvReconciler.ENV_VAR_NAME + "}" })
public class NamespaceFromEnvReconciler implements Reconciler<Pod> {
    public static final String NAME = "fromenv";
    public static final String ENV_VAR_NAME = "NAMESPACE_FROM_ENV";
    static final String FROM_ENV_VAR_NS = "fromEnvVarNS";

    @Override
    public UpdateControl<Pod> reconcile(Pod pod, Context<Pod> context) throws Exception {
        return UpdateControl.noUpdate();
    }
}
