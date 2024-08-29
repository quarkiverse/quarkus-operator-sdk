package io.quarkiverse.operatorsdk.deployment;

import static io.quarkiverse.operatorsdk.deployment.AddClusterRolesDecorator.JOSDK_CRD_VALIDATING_CLUSTER_ROLE_NAME;
import static io.quarkiverse.operatorsdk.deployment.AddClusterRolesDecorator.getClusterRoleName;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBindingBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleRef;
import io.fabric8.kubernetes.api.model.rbac.RoleRefBuilder;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.quarkiverse.operatorsdk.runtime.BuildTimeOperatorConfiguration;
import io.quarkiverse.operatorsdk.runtime.QuarkusControllerConfiguration;

@SuppressWarnings("rawtypes")
public class AddRoleBindingsDecorator {

    public static final String CLUSTER_ROLE = "ClusterRole";
    protected static final String RBAC_AUTHORIZATION_GROUP = "rbac.authorization.k8s.io";
    public static final RoleRef CRD_VALIDATING_ROLE_REF = new RoleRef(RBAC_AUTHORIZATION_GROUP, CLUSTER_ROLE,
            JOSDK_CRD_VALIDATING_CLUSTER_ROLE_NAME);
    protected static final String SERVICE_ACCOUNT = "ServiceAccount";
    private static final Logger log = Logger.getLogger(AddRoleBindingsDecorator.class);
    private static final ConcurrentMap<QuarkusControllerConfiguration, BindingsHolder> cachedBindings = new ConcurrentHashMap<>();
    private static final Optional<String> deployNamespace = ConfigProvider.getConfig()
            .getOptionalValue("quarkus.kubernetes.namespace", String.class);

    private static class BindingsHolder {
        private List<RoleBinding> roleBindings;
        private List<ClusterRoleBinding> clusterRoleBindings;

        public List<RoleBinding> getRoleBindings() {
            return roleBindings;
        }

        public void setRoleBindings(List<RoleBinding> roleBindings) {
            this.roleBindings = roleBindings;
        }

        public List<ClusterRoleBinding> getClusterRoleBindings() {
            return clusterRoleBindings;
        }

        public void setClusterRoleBindings(List<ClusterRoleBinding> clusterRoleBindings) {
            this.clusterRoleBindings = clusterRoleBindings;
        }
    }

    public static String getCRDValidatingBindingName(String controllerName) {
        return controllerName + "-crd-validating-role-binding";
    }

    public static String getClusterRoleBindingName(String controllerName) {
        return controllerName + "-cluster-role-binding";
    }

    public static String getRoleBindingName(String controllerName) {
        return controllerName + "-role-binding";
    }

    public static String getSpecificRoleBindingName(String controllerName, String roleRefName) {
        return roleRefName + "-" + getRoleBindingName(controllerName);
    }

    public static String getSpecificRoleBindingName(String controllerName, RoleRef roleRef) {
        return getSpecificRoleBindingName(controllerName, roleRef.getName());
    }

    private static RoleRef createDefaultRoleRef(String controllerName) {
        return new RoleRefBuilder()
                .withApiGroup(RBAC_AUTHORIZATION_GROUP)
                .withKind(CLUSTER_ROLE)
                .withName(getClusterRoleName(controllerName))
                .build();
    }

    private static RoleBinding createRoleBinding(String roleBindingName,
            String serviceAccountName,
            String targetNamespace,
            RoleRef roleRef) {
        final var nsMsg = (targetNamespace == null ? "current" : "'" + targetNamespace + "'") + " namespace";
        log.infov("Creating ''{0}'' RoleBinding to be applied to {1}", roleBindingName, nsMsg);
        return new RoleBindingBuilder()
                .withNewMetadata()
                .withName(roleBindingName)
                .withNamespace(targetNamespace)
                .endMetadata()
                .withRoleRef(roleRef)
                .addNewSubject(null, SERVICE_ACCOUNT, serviceAccountName, deployNamespace(serviceAccountName))
                .build();
    }

    private static String deployNamespace(String serviceAccountName) {
        return deployNamespace.orElse(null);
    }

    private static ClusterRoleBinding createClusterRoleBinding(String serviceAccountName,
            String controllerName, String bindingName, String controllerConfMessage,
            RoleRef roleRef) {
        outputWarningIfNeeded(controllerName, bindingName, controllerConfMessage);
        roleRef = roleRef == null ? createDefaultRoleRef(controllerName) : roleRef;
        final var ns = deployNamespace(serviceAccountName);
        log.infov("Creating ''{0}'' ClusterRoleBinding to be applied to ''{1}'' namespace", bindingName, ns);
        return new ClusterRoleBindingBuilder()
                .withNewMetadata().withName(bindingName)
                .endMetadata()
                .withRoleRef(roleRef)
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

    public static List<RoleBinding> createRoleBindings(Collection<QuarkusControllerConfiguration<?>> configs,
            BuildTimeOperatorConfiguration operatorConfiguration, String serviceAccountName) {
        return configs.stream()
                .flatMap(config -> bindingsFor(config, operatorConfiguration, serviceAccountName).getRoleBindings().stream())
                .toList();
    }

    public static List<ClusterRoleBinding> createClusterRoleBindings(Collection<QuarkusControllerConfiguration<?>> configs,
            BuildTimeOperatorConfiguration operatorConfiguration, String serviceAccountName) {
        return configs.stream()
                .flatMap(config -> bindingsFor(config, operatorConfiguration, serviceAccountName).getClusterRoleBindings()
                        .stream())
                .toList();
    }

    private static BindingsHolder bindingsFor(QuarkusControllerConfiguration<?> controllerConfiguration,
            BuildTimeOperatorConfiguration operatorConfiguration, String serviceAccountName) {
        var bindings = cachedBindings.get(controllerConfiguration);
        if (bindings != null) {
            return bindings;
        } else {
            bindings = new BindingsHolder();
            cachedBindings.put(controllerConfiguration, bindings);
        }

        final var controllerName = controllerConfiguration.getName();

        // retrieve which namespaces should be used to generate either from annotation or from the build time configuration
        final InformerConfiguration<?> informerConfig = controllerConfiguration.getInformerConfig();
        final var desiredWatchedNamespaces = informerConfig.getNamespaces();

        // if we validate the CRDs, also create a binding for the CRD validating role
        final List<RoleBinding> roleBindings = new LinkedList<>();
        final List<ClusterRoleBinding> clusterRoleBindings = new LinkedList<>();
        if (operatorConfiguration.crd().validate()) {
            final var crBindingName = getCRDValidatingBindingName(controllerName);
            final var crdValidatorRoleBinding = createClusterRoleBinding(serviceAccountName, controllerName,
                    crBindingName, "validate CRDs", CRD_VALIDATING_ROLE_REF);
            clusterRoleBindings.add(crdValidatorRoleBinding);
        }

        final var roleBindingName = getRoleBindingName(controllerName);
        if (informerConfig.watchCurrentNamespace()) {
            // create a RoleBinding that will be applied in the current namespace if watching only the current NS
            roleBindings.add(createRoleBinding(roleBindingName, serviceAccountName, null,
                    createDefaultRoleRef(controllerName)));
            // add additional Role Bindings
            controllerConfiguration.getAdditionalRBACRoleRefs().forEach(
                    roleRef -> {
                        final var specificRoleBindingName = getSpecificRoleBindingName(controllerName, roleRef);
                        roleBindings.add(createRoleBinding(specificRoleBindingName, serviceAccountName, null, roleRef));
                    });
        } else if (informerConfig.watchAllNamespaces()) {
            clusterRoleBindings.add(createClusterRoleBinding(serviceAccountName, controllerName,
                    getClusterRoleBindingName(controllerName), "watch all namespaces",
                    null));
            // add additional cluster role bindings only if they target cluster roles
            controllerConfiguration.getAdditionalRBACRoleRefs().forEach(
                    roleRef -> {
                        if (!CLUSTER_ROLE.equals(roleRef.getKind())) {
                            log.warnv("Cannot create a ClusterRoleBinding for RoleRef ''{0}'' because it's not a ClusterRole",
                                    roleRef);
                        } else {
                            clusterRoleBindings.add(createClusterRoleBinding(serviceAccountName, controllerName,
                                    roleRef.getName() + "-" + getClusterRoleBindingName(controllerName),
                                    "watch all namespaces", roleRef));
                        }
                    });
        } else {
            // create a RoleBinding using either the provided deployment namespace or the desired watched namespace name
            desiredWatchedNamespaces
                    .forEach(ns -> {
                        roleBindings.add(createRoleBinding(roleBindingName, serviceAccountName, ns,
                                createDefaultRoleRef(controllerName)));
                        //add additional Role Bindings
                        controllerConfiguration.getAdditionalRBACRoleRefs()
                                .forEach(roleRef -> {
                                    final var specificRoleBindingName = getSpecificRoleBindingName(controllerName, roleRef);
                                    roleBindings.add(createRoleBinding(specificRoleBindingName, serviceAccountName,
                                            ns, roleRef));
                                });
                    });
        }

        // add items to cache
        bindings.setRoleBindings(roleBindings);
        bindings.setClusterRoleBindings(clusterRoleBindings);
        return bindings;
    }

    public static String getCRDValidatingBindingName(String controllerName) {
        return controllerName + "-crd-validating-role-binding";
    }

    public static String getClusterRoleBindingName(String controllerName) {
        return controllerName + "-cluster-role-binding";
    }

    public static String getRoleBindingName(String controllerName) {
        return controllerName + "-role-binding";
    }

    public static String getSpecificRoleBindingName(String controllerName, String roleRefName) {
        return roleRefName + "-" + getRoleBindingName(controllerName);
    }

    public static String getSpecificRoleBindingName(String controllerName, RoleRef roleRef) {
        return getSpecificRoleBindingName(controllerName, roleRef.getName());
    }

    private static RoleRef createDefaultRoleRef(String controllerName) {
        return new RoleRefBuilder()
                .withApiGroup(RBAC_AUTHORIZATION_GROUP)
                .withKind(CLUSTER_ROLE)
                .withName(getClusterRoleName(controllerName))
                .build();
    }

    private static RoleBinding createRoleBinding(String roleBindingName,
            String serviceAccountName,
            String targetNamespace,
            RoleRef roleRef) {
        final var nsMsg = (targetNamespace == null ? "current" : "'" + targetNamespace + "'") + " namespace";
        log.infov("Creating ''{0}'' RoleBinding to be applied to {1}", roleBindingName, nsMsg);
        return new RoleBindingBuilder()
                .withNewMetadata()
                .withName(roleBindingName)
                .withNamespace(targetNamespace)
                .endMetadata()
                .withRoleRef(roleRef)
                .addNewSubject(null, SERVICE_ACCOUNT, serviceAccountName,
                        deployNamespace.orElse(null))
                .build();
    }

    private static ClusterRoleBinding createClusterRoleBinding(String serviceAccountName,
            String controllerName, String bindingName, String controllerConfMessage,
            RoleRef roleRef) {
        outputWarningIfNeeded(controllerName, bindingName, controllerConfMessage);
        roleRef = roleRef == null ? createDefaultRoleRef(controllerName) : roleRef;
        final var ns = deployNamespace.orElse(null);
        log.infov("Creating ''{0}'' ClusterRoleBinding to be applied to ''{1}'' namespace", bindingName, ns);
        return new ClusterRoleBindingBuilder()
                .withNewMetadata().withName(bindingName)
                .endMetadata()
                .withRoleRef(roleRef)
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
