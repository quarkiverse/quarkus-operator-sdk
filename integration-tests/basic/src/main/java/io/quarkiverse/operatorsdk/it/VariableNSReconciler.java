package io.quarkiverse.operatorsdk.it;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration(name = VariableNSReconciler.NAME)
public class VariableNSReconciler implements Reconciler<Deployment> {

    public static final String NAME = "variablens";
    public static final String ENV_VAR_NAME = "VARIABLE_NS_ENV";
    public static final String EXPECTED_NS_VALUE = "variableNSFromEnv";

    @Override
    public UpdateControl<Deployment> reconcile(Deployment deployment, Context<Deployment> context)
            throws Exception {
        return null;
    }
}
