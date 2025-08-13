package io.quarkiverse.operatorsdk.deployment;

import static io.quarkiverse.operatorsdk.deployment.ClusterRoles.JOSDK_CRD_VALIDATING_CLUSTER_ROLE_NAME;
import static io.quarkiverse.operatorsdk.deployment.ClusterRoles.getClusterRoleName;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBindingBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleRef;
import io.fabric8.kubernetes.api.model.rbac.RoleRefBuilder;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.quarkiverse.operatorsdk.runtime.BuildTimeOperatorConfiguration;
import io.quarkiverse.operatorsdk.runtime.QuarkusBuildTimeControllerConfiguration;

@SuppressWarnings("rawtypes")
public class RoleBindings {

    public static final String CLUSTER_ROLE = "ClusterRole";
    protected static final String RBAC_AUTHORIZATION_GROUP = "rbac.authorization.k8s.io";
    public static final RoleRef CRD_VALIDATING_ROLE_REF = new RoleRef(RBAC_AUTHORIZATION_GROUP, CLUSTER_ROLE,
            JOSDK_CRD_VALIDATING_CLUSTER_ROLE_NAME);
    protected static final String SERVICE_ACCOUNT = "ServiceAccount";
    private static final Logger log = Logger.getLogger(RoleBindings.class);
    private static final ConcurrentMap<QuarkusBuildTimeControllerConfiguration, BindingsHolder> cachedBindings = new ConcurrentHashMap<>();

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
            String serviceAccountNamespace,
            String targetNamespace,
            RoleRef roleRef) {
        final var nsMsg = (targetNamespace == null ? "current" : "'" + targetNamespace + "'") + " namespace";
        log.infof("Creating '%s' RoleBinding to be applied to %s", roleBindingName, nsMsg);
        return new RoleBindingBuilder()
                .withNewMetadata()
                .withName(roleBindingName)
                .withNamespace(targetNamespace)
                .endMetadata()
                .withRoleRef(roleRef)
                .addNewSubject(null, SERVICE_ACCOUNT, serviceAccountName, serviceAccountNamespace)
                .build();
    }

    private static ClusterRoleBinding createClusterRoleBinding(String bindingName,
            String serviceAccountName,
            String serviceAccountNamespace,
            String controllerName,
            String controllerConfMessage,
            RoleRef roleRef) {
        outputWarningIfNeeded(serviceAccountNamespace, controllerName, bindingName, controllerConfMessage);
        roleRef = roleRef == null ? createDefaultRoleRef(controllerName) : roleRef;
        log.infof("Creating '%s' ClusterRoleBinding to be applied to '%s' namespace", bindingName,
                serviceAccountNamespace);
        return new ClusterRoleBindingBuilder()
                .withNewMetadata().withName(bindingName)
                .endMetadata()
                .withRoleRef(roleRef)
                .addNewSubject()
                .withKind(SERVICE_ACCOUNT).withName(serviceAccountName)
                .withNamespace(serviceAccountNamespace != null ? serviceAccountNamespace : "default")
                .endSubject()
                .build();
    }

    private static void outputWarningIfNeeded(String serviceAccountNamespace, String controllerName, String crBindingName,
            String controllerConfMessage) {
        // the decorator can be called several times but we only want to output the warning once
        if (serviceAccountNamespace == null || serviceAccountNamespace.isEmpty()) {
            log.warnf(
                    "'%s' controller is configured to "
                            + controllerConfMessage
                            + ", this requires a ClusterRoleBinding which REQUIRES a namespace for the operator ServiceAccount, which has NOT been provided. You can specify the ServiceAccount's namespace using the 'quarkus.kubernetes.rbac.service-accounts.<service account name>.namespace=<service account namespace>' property (or, alternatively, 'quarkus.kubernetes.namespace', though using this property will use the specified namespace for ALL your resources. Leaving the namespace blank to be provided by the user by editing the '%s' ClusterRoleBinding to provide the namespace in which the operator will be deployed.",
                    controllerName, crBindingName);
        }
    }

    public static List<RoleBinding> createRoleBindings(Collection<QuarkusBuildTimeControllerConfiguration<?>> configs,
            BuildTimeOperatorConfiguration operatorConfiguration, String serviceAccountName, String serviceAccountNamespace) {
        return configs.stream()
                .flatMap(config -> bindingsFor(config, operatorConfiguration, serviceAccountName, serviceAccountNamespace)
                        .getRoleBindings().stream())
                .toList();
    }

    public static List<ClusterRoleBinding> createClusterRoleBindings(
            Collection<QuarkusBuildTimeControllerConfiguration<?>> configs,
            BuildTimeOperatorConfiguration operatorConfiguration, String serviceAccountName, String serviceAccountNamespace) {
        return configs.stream()
                .flatMap(config -> bindingsFor(config, operatorConfiguration, serviceAccountName, serviceAccountNamespace)
                        .getClusterRoleBindings()
                        .stream())
                .toList();
    }

    private static BindingsHolder bindingsFor(QuarkusBuildTimeControllerConfiguration<?> controllerConfiguration,
            BuildTimeOperatorConfiguration operatorConfiguration, String serviceAccountName, String serviceAccountNamespace) {
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
            final var crdValidatorRoleBinding = createClusterRoleBinding(crBindingName, serviceAccountName,
                    serviceAccountNamespace, controllerName,
                    "validate CRDs", CRD_VALIDATING_ROLE_REF);
            clusterRoleBindings.add(crdValidatorRoleBinding);
        }

        final var roleBindingName = getRoleBindingName(controllerName);
        if (informerConfig.watchCurrentNamespace()) {
            // create a RoleBinding that will be applied in the current namespace if watching only the current NS
            roleBindings.add(createRoleBinding(roleBindingName, serviceAccountName, serviceAccountNamespace,
                    null, createDefaultRoleRef(controllerName)));
            // add additional Role Bindings
            controllerConfiguration.getAdditionalRBACRoleRefs().forEach(
                    roleRef -> {
                        final var specificRoleBindingName = getSpecificRoleBindingName(controllerName, roleRef);
                        roleBindings.add(createRoleBinding(specificRoleBindingName, serviceAccountName, serviceAccountNamespace,
                                null, roleRef));
                    });
        } else if (informerConfig.watchAllNamespaces()) {
            clusterRoleBindings.add(createClusterRoleBinding(getClusterRoleBindingName(controllerName), serviceAccountName,
                    serviceAccountNamespace, controllerName,
                    "watch all namespaces",
                    null));
            // add additional cluster role bindings only if they target cluster roles
            controllerConfiguration.getAdditionalRBACRoleRefs().forEach(
                    roleRef -> {
                        if (!CLUSTER_ROLE.equals(roleRef.getKind())) {
                            log.warnf("Cannot create a ClusterRoleBinding for RoleRef '%s' because it's not a ClusterRole",
                                    roleRef);
                        } else {
                            clusterRoleBindings.add(createClusterRoleBinding(
                                    roleRef.getName() + "-" + getClusterRoleBindingName(controllerName), serviceAccountName,
                                    serviceAccountNamespace, controllerName,
                                    "watch all namespaces", roleRef));
                        }
                    });
        } else {
            // create a RoleBinding using either the provided deployment namespace or the desired watched namespace name
            desiredWatchedNamespaces
                    .forEach(ns -> {
                        roleBindings.add(createRoleBinding(roleBindingName, serviceAccountName, serviceAccountNamespace,
                                ns, createDefaultRoleRef(controllerName)));
                        //add additional Role Bindings
                        controllerConfiguration.getAdditionalRBACRoleRefs()
                                .forEach(roleRef -> {
                                    final var specificRoleBindingName = getSpecificRoleBindingName(controllerName, roleRef);
                                    roleBindings.add(createRoleBinding(specificRoleBindingName, serviceAccountName,
                                            serviceAccountNamespace,
                                            ns, roleRef));
                                });
                    });
        }

        // add items to cache
        bindings.setRoleBindings(roleBindings);
        bindings.setClusterRoleBindings(clusterRoleBindings);
        return bindings;
    }
}
