package io.quarkiverse.operatorsdk.bundle.sources;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.quarkiverse.operatorsdk.annotations.CSVMetadata;
import io.quarkiverse.operatorsdk.annotations.RBACRule;

@CSVMetadata(bundleName = "second-operator")
@RBACRule(apiGroups = SecondReconciler.RBAC_RULE_GROUP, resources = SecondReconciler.RBAC_RULE_RES, verbs = SecondReconciler.RBAC_RULE_VERBS)
@ControllerConfiguration(namespaces = "foo")
public class SecondReconciler implements Reconciler<Second> {
    public static final String RBAC_RULE_GROUP = "halkyon.io";
    public static final String RBAC_RULE_RES = "SomeResource";
    public static final String RBAC_RULE_VERBS = "write";

    @Override
    public UpdateControl<Second> reconcile(Second request, Context<Second> context) {
        return UpdateControl.noUpdate();
    }
}