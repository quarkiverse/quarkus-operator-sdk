package io.quarkiverse.operatorsdk.test.sources;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.quarkiverse.operatorsdk.annotations.RBACCRoleRef;
import io.quarkiverse.operatorsdk.annotations.RBACRule;
import io.quarkiverse.operatorsdk.annotations.RBACVerbs;

@ControllerConfiguration(name = SimpleReconciler.NAME)
@RBACRule(verbs = RBACVerbs.UPDATE, apiGroups = SimpleReconciler.CERTIFICATES_K8S_IO_GROUP, resources = SimpleReconciler.ADDITIONAL_UPDATE_RESOURCE)
@RBACRule(verbs = SimpleReconciler.SIGNERS_VERB, apiGroups = SimpleReconciler.CERTIFICATES_K8S_IO_GROUP, resources = SimpleReconciler.SIGNERS_RESOURCE, resourceNames = SimpleReconciler.SIGNERS_RESOURCE_NAMES)
@RBACCRoleRef(name = SimpleReconciler.ROLE_REF_NAME, kind = RBACCRoleRef.RoleKind.ClusterRole)
public class SimpleReconciler implements Reconciler<SimpleCR> {

    public static final String NAME = "simple";
    public static final String CERTIFICATES_K8S_IO_GROUP = "certificates.k8s.io";
    public static final String ADDITIONAL_UPDATE_RESOURCE = "certificatesigningrequests/approval";
    public static final String SIGNERS_VERB = "approve";
    public static final String SIGNERS_RESOURCE = "signers";
    public static final String SIGNERS_RESOURCE_NAMES = "kubernetes.io/kubelet-serving";
    public static final String ROLE_REF_NAME = "system:auth-delegator";

    @Override
    public UpdateControl<SimpleCR> reconcile(SimpleCR simpleCR, Context<SimpleCR> context) {
        return null;
    }
}
