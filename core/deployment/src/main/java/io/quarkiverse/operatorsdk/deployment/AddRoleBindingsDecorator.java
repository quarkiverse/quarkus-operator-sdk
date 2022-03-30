package io.quarkiverse.operatorsdk.deployment;

import static io.quarkiverse.operatorsdk.deployment.AddClusterRolesDecorator.getClusterRoleName;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.eclipse.microprofile.config.ConfigProvider;

import io.dekorate.kubernetes.decorator.ResourceProvidingDecorator;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBindingBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import io.quarkiverse.operatorsdk.runtime.QuarkusControllerConfiguration;

@SuppressWarnings("rawtypes")
public class AddRoleBindingsDecorator extends ResourceProvidingDecorator<KubernetesListBuilder> {

    protected static final String RBAC_AUTHORIZATION_GROUP = "rbac.authorization.k8s.io";
    protected static final String CLUSTER_ROLE = "ClusterRole";
    protected static final String SERVICE_ACCOUNT = "ServiceAccount";
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
            QuarkusControllerConfiguration<?> config = entry.getValue();
            if (config.watchCurrentNamespace()) {
                // create a RoleBinding that will be applied in the current namespace if watching only the current NS
                list.addToItems(new RoleBindingBuilder()
                        .withNewMetadata()
                        .withName(controllerName + "-role-binding")
                        .endMetadata()
                        .withNewRoleRef(RBAC_AUTHORIZATION_GROUP, CLUSTER_ROLE,
                                getClusterRoleName(controllerName))
                        .addNewSubject(null, SERVICE_ACCOUNT, serviceAccountName, null)
                        .build());
            } else if (config.watchAllNamespaces()) {
                handleClusterRoleBinding(list, serviceAccountName, controllerName,
                        controllerName + "-cluster-role-binding", "watch all namespaces",
                        getClusterRoleName(controllerName));
            } else {
                config.getEffectiveNamespaces().forEach(ns -> list.addToItems(
                        new RoleBindingBuilder()
                                .withNewMetadata()
                                .withName(controllerName + "-role-binding")
                                .withNamespace(ns)
                                .endMetadata()
                                .withNewRoleRef(RBAC_AUTHORIZATION_GROUP, CLUSTER_ROLE,
                                        getClusterRoleName(controllerName))
                                .addNewSubject(null, SERVICE_ACCOUNT, serviceAccountName, null)
                                .build()));
            }

            // if we validate the CRDs, also create a binding for the CRD validating role
            if (validateCRDs) {
                final var crBindingName = controllerName + "-crd-validating-role-binding";
                handleClusterRoleBinding(list, serviceAccountName, controllerName, crBindingName, "validate CRDs",
                        AddClusterRolesDecorator.JOSDK_CRD_VALIDATING_CLUSTER_ROLE);
            }
        }
    }

    private void handleClusterRoleBinding(KubernetesListBuilder list, String serviceAccountName,
            String controllerName, String bindingName, String controllerConfMessage,
            String clusterRoleName) {
        final var namespace = ConfigProvider.getConfig()
                .getOptionalValue("quarkus.kubernetes.namespace", String.class);
        outputWarningIfNeeded(controllerName, bindingName, namespace, controllerConfMessage);
        list.addToItems(new ClusterRoleBindingBuilder()
                .withNewMetadata().withName(bindingName)
                .endMetadata()
                .withNewRoleRef(RBAC_AUTHORIZATION_GROUP, CLUSTER_ROLE, clusterRoleName)
                .addNewSubject()
                .withKind(SERVICE_ACCOUNT).withName(serviceAccountName).withNamespace(namespace.orElse(null))
                .endSubject()
                .build());
    }

    private void outputWarningIfNeeded(String controllerName, String crBindingName,
            Optional<String> namespace, String controllerConfMessage) {
        // the decorator can be called several times but we only want to output the warning once
        if (namespace.isEmpty()
                && alreadyLogged.putIfAbsent(controllerName + crBindingName, new Object()) == null) {
            OperatorSDKProcessor.log.warnv(
                    "''{0}'' controller is configured to "
                            + controllerConfMessage
                            + ", this requires a ClusterRoleBinding for which we MUST specify the namespace of the operator ServiceAccount. This can be specified by setting the ''quarkus.kubernetes.namespace'' property. However, as this property is not set, we are leaving the namespace blank to be provided by the user by editing the ''{1}'' ClusterRoleBinding to provide the namespace in which the operator will be deployed.",
                    controllerName, crBindingName);
        }
    }
}
