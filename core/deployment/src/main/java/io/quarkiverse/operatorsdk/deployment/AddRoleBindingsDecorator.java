package io.quarkiverse.operatorsdk.deployment;

import static io.quarkiverse.operatorsdk.deployment.AddClusterRolesDecorator.getClusterRoleName;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.dekorate.kubernetes.decorator.ResourceProvidingDecorator;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBindingBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import io.quarkiverse.operatorsdk.runtime.QuarkusControllerConfiguration;

public class AddRoleBindingsDecorator extends ResourceProvidingDecorator<KubernetesListBuilder> {

    private final Map<String, QuarkusControllerConfiguration> configs;
    private final boolean validateCRDs;
    private static final ConcurrentMap<String, Object> alreadyLogged = new ConcurrentHashMap<>();

    public AddRoleBindingsDecorator(Map<String, QuarkusControllerConfiguration> configs,
            boolean validateCRDs) {
        this.configs = configs;
        this.validateCRDs = validateCRDs;
    }

    @Override
    public void visit(KubernetesListBuilder list) {
        final var serviceAccountName = getMandatoryDeploymentMetadata(list).getName();
        for (Entry<String, QuarkusControllerConfiguration> entry : configs.entrySet()) {
            String controllerName = entry.getKey();
            QuarkusControllerConfiguration config = entry.getValue();
            if (config.watchCurrentNamespace()) {
                // create a RoleBinding that will be applied in the current namespace if watching only the current NS
                list.addToItems(new RoleBindingBuilder()
                        .withNewMetadata()
                        .withName(controllerName + "-role-binding")
                        .endMetadata()
                        .withNewRoleRef("rbac.authorization.k8s.io", "ClusterRole",
                                getClusterRoleName(controllerName))
                        .addNewSubject(null, "ServiceAccount", serviceAccountName, null)
                        .build());
            } else if (config.watchAllNamespaces()) {
                final var crBindingName = controllerName + "-cluster-role-binding";
                // the decorator can be called several times but we only want to output the warning once
                if (alreadyLogged.putIfAbsent(controllerName, new Object()) != null) {
                    OperatorSDKProcessor.log.warnv(
                            "''{0}'' controller is configured to watch all namespaces, this requires a ClusterRoleBinding for which we MUST specify the namespace of the operator ServiceAccount. However, at this information is not known at build time, we are leaving it blank and needs to be provided by the user by editing the ''{1}'' ClusterRoleBinding to provide the namespace in which the operator will be deployed.",
                            controllerName, crBindingName);
                }
                list.addToItems(new ClusterRoleBindingBuilder()
                        .withNewMetadata().withName(crBindingName)
                        .endMetadata()
                        .withNewRoleRef("rbac.authorization.k8s.io", "ClusterRole",
                                getClusterRoleName(controllerName))
                        .addNewSubject(null, "ServiceAccount", serviceAccountName, null)
                        .build());
            } else {
                config.getEffectiveNamespaces().forEach(ns -> list.addToItems(
                        new RoleBindingBuilder()
                                .withNewMetadata()
                                .withName(controllerName + "-role-binding")
                                .withNamespace((String) ns)
                                .endMetadata()
                                .withNewRoleRef("rbac.authorization.k8s.io", "ClusterRole",
                                        getClusterRoleName(controllerName))
                                .addNewSubject(null, "ServiceAccount", serviceAccountName, null)
                                .build()));
            }

            // if we validate the CRDs, also create a binding for the CRD validating role
            if (validateCRDs) {
                list.addToItems(new RoleBindingBuilder()
                        .withNewMetadata()
                        .withName(controllerName + "-crd-validating-role-binding")
                        .endMetadata()
                        .withNewRoleRef("rbac.authorization.k8s.io", "ClusterRole",
                                AddClusterRolesDecorator.JOSDK_CRD_VALIDATING_CLUSTER_ROLE)
                        .addNewSubject(null, "ServiceAccount", serviceAccountName, null)
                        .build());
            }
        }
    }
}
