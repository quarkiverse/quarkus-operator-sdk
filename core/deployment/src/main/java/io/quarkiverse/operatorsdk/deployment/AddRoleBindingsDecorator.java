package io.quarkiverse.operatorsdk.deployment;

import static io.quarkiverse.operatorsdk.deployment.AddClusterRolesDecorator.getClusterRoleName;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import io.dekorate.kubernetes.decorator.ResourceProvidingDecorator;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBindingBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import io.quarkiverse.operatorsdk.runtime.BuildTimeOperatorConfiguration;
import io.quarkiverse.operatorsdk.runtime.QuarkusControllerConfiguration;

@SuppressWarnings("rawtypes")
public class AddRoleBindingsDecorator extends ResourceProvidingDecorator<KubernetesListBuilder> {

    private static final Logger log = Logger.getLogger(AddRoleBindingsDecorator.class);
    protected static final String RBAC_AUTHORIZATION_GROUP = "rbac.authorization.k8s.io";
    protected static final String CLUSTER_ROLE = "ClusterRole";
    protected static final String SERVICE_ACCOUNT = "ServiceAccount";
    private final Collection<QuarkusControllerConfiguration> configs;
    private final BuildTimeOperatorConfiguration operatorConfiguration;
    private static final ConcurrentMap<QuarkusControllerConfiguration, List<HasMetadata>> cachedBindings = new ConcurrentHashMap<>();
    private static final Optional<String> deployNamespace = ConfigProvider.getConfig()
            .getOptionalValue("quarkus.kubernetes.namespace", String.class);

    public AddRoleBindingsDecorator(Collection<QuarkusControllerConfiguration> configs,
            BuildTimeOperatorConfiguration operatorConfiguration) {
        this.configs = configs;
        this.operatorConfiguration = operatorConfiguration;
    }

    @Override
    public void visit(KubernetesListBuilder list) {
        final var serviceAccountName = getMandatoryDeploymentMetadata(list).getName();
        configs.forEach(config -> {
            final var toAdd = cachedBindings.computeIfAbsent(config, c -> bindingsFor(c, serviceAccountName));
            list.addAllToItems(toAdd);
        });
    }

    private List<HasMetadata> bindingsFor(QuarkusControllerConfiguration<?> controllerConfiguration,
            String serviceAccountName) {
        final var controllerName = controllerConfiguration.getName();

        // retrieve which namespaces should be used to generate either from annotation or from the build time configuration
        final var desiredWatchedNamespaces = controllerConfiguration.getNamespaces();

        // if we validate the CRDs, also create a binding for the CRD validating role
        List<HasMetadata> itemsToAdd;
        if (operatorConfiguration.crd.validate) {
            final var crBindingName = controllerName + "-crd-validating-role-binding";
            final var crdValidatorRoleBinding = createClusterRoleBinding(serviceAccountName, controllerName,
                    crBindingName, "validate CRDs",
                    AddClusterRolesDecorator.JOSDK_CRD_VALIDATING_CLUSTER_ROLE);
            itemsToAdd = new ArrayList<>(desiredWatchedNamespaces.size() + 1);
            itemsToAdd.add(crdValidatorRoleBinding);
        } else {
            itemsToAdd = new ArrayList<>(desiredWatchedNamespaces.size());
        }

        final var roleBindingName = getRoleBindingName(controllerName);
        if (controllerConfiguration.watchCurrentNamespace()) {
            // create a RoleBinding that will be applied in the current namespace if watching only the current NS
            itemsToAdd.add(createRoleBinding(roleBindingName, controllerName, serviceAccountName, null));
        } else if (controllerConfiguration.watchAllNamespaces()) {
            itemsToAdd.add(createClusterRoleBinding(serviceAccountName, controllerName,
                    controllerName + "-cluster-role-binding", "watch all namespaces",
                    getClusterRoleName(controllerName)));
        } else {
            // create a RoleBinding using either the provided deployment namespace or the desired watched namespace name
            desiredWatchedNamespaces
                    .forEach(ns -> itemsToAdd.add(createRoleBinding(roleBindingName, controllerName, serviceAccountName, ns)));
        }

        return itemsToAdd;
    }

    public static String getRoleBindingName(String controllerName) {
        return controllerName + "-role-binding";
    }

    private static RoleBinding createRoleBinding(String roleBindingName, String controllerName,
            String serviceAccountName, String namespace) {
        final var nsMsg = (namespace == null ? "current" : "'" + namespace + "'") + " namespace";
        log.infov("Creating ''{0}'' RoleBinding to be applied to {1}", roleBindingName, nsMsg);
        return new RoleBindingBuilder()
                .withNewMetadata()
                .withName(roleBindingName)
                .withNamespace(deployNamespace.orElse(namespace))
                .endMetadata()
                .withNewRoleRef(RBAC_AUTHORIZATION_GROUP, CLUSTER_ROLE, getClusterRoleName(controllerName))
                .addNewSubject(null, SERVICE_ACCOUNT, serviceAccountName,
                        deployNamespace.orElse(null))
                .build();
    }

    private static ClusterRoleBinding createClusterRoleBinding(String serviceAccountName,
            String controllerName, String bindingName, String controllerConfMessage,
            String clusterRoleName) {
        outputWarningIfNeeded(controllerName, bindingName, controllerConfMessage);
        final var ns = deployNamespace.orElse(null);
        log.infov("Creating ''{0}'' ClusterRoleBinding to be applied to ''{1}'' namespace", bindingName, ns);
        return new ClusterRoleBindingBuilder()
                .withNewMetadata().withName(bindingName)
                .endMetadata()
                .withNewRoleRef(RBAC_AUTHORIZATION_GROUP, CLUSTER_ROLE, clusterRoleName)
                .addNewSubject()
                .withKind(SERVICE_ACCOUNT).withName(serviceAccountName).withNamespace(ns)
                .endSubject()
                .build();
    }

    private static void outputWarningIfNeeded(String controllerName, String crBindingName, String controllerConfMessage) {
        // the decorator can be called several times but we only want to output the warning once
        if (deployNamespace.isEmpty()) {
            log.warnv(
                    "''{0}'' controller is configured to "
                            + controllerConfMessage
                            + ", this requires a ClusterRoleBinding for which we MUST specify the namespace of the operator ServiceAccount. This can be specified by setting the ''quarkus.kubernetes.namespace'' property. However, as this property is not set, we are leaving the namespace blank to be provided by the user by editing the ''{1}'' ClusterRoleBinding to provide the namespace in which the operator will be deployed.",
                    controllerName, crBindingName);
        }
    }
}
