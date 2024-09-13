package io.quarkiverse.operatorsdk.deployment;

import static io.quarkiverse.operatorsdk.deployment.AddClusterRolesDecorator.JOSDK_CRD_VALIDATING_CLUSTER_ROLE_NAME;
import static io.quarkiverse.operatorsdk.deployment.AddClusterRolesDecorator.getClusterRoleName;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.jboss.logging.Logger;

import io.dekorate.kubernetes.decorator.Decorator;
import io.dekorate.kubernetes.decorator.ResourceProvidingDecorator;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBindingBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleRef;
import io.fabric8.kubernetes.api.model.rbac.RoleRefBuilder;
import io.quarkiverse.operatorsdk.runtime.BuildTimeOperatorConfiguration;
import io.quarkiverse.operatorsdk.runtime.QuarkusControllerConfiguration;
import io.quarkus.kubernetes.deployment.AddServiceAccountResourceDecorator;

@SuppressWarnings("rawtypes")
public class AddRoleBindingsDecorator extends ResourceProvidingDecorator<KubernetesListBuilder> {

    public static final String CLUSTER_ROLE = "ClusterRole";
    protected static final String RBAC_AUTHORIZATION_GROUP = "rbac.authorization.k8s.io";
    public static final RoleRef CRD_VALIDATING_ROLE_REF = new RoleRef(RBAC_AUTHORIZATION_GROUP, CLUSTER_ROLE,
            JOSDK_CRD_VALIDATING_CLUSTER_ROLE_NAME);
    protected static final String SERVICE_ACCOUNT = "ServiceAccount";
    private static final Logger log = Logger.getLogger(AddRoleBindingsDecorator.class);
    private static final ConcurrentMap<QuarkusControllerConfiguration, List<HasMetadata>> cachedBindings = new ConcurrentHashMap<>();
    private final Collection<QuarkusControllerConfiguration<?>> configs;
    private final BuildTimeOperatorConfiguration operatorConfiguration;
    private final String serviceAccountName;
    private final String serviceAccountNamespace;

    public AddRoleBindingsDecorator(Collection<QuarkusControllerConfiguration<?>> configs,
            BuildTimeOperatorConfiguration operatorConfiguration, String serviceAccountName, String serviceAccountNamespace) {
        this.configs = configs;
        this.operatorConfiguration = operatorConfiguration;
        this.serviceAccountName = serviceAccountName;
        this.serviceAccountNamespace = serviceAccountNamespace;
    }

    public static String getCRDValidatingBindingName(String controllerName) {
        return controllerName + "-crd-validating-role-binding";
    }

    private static String getClusterRoleBindingName(String controllerName) {
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
                .withApiGroup(RBAC_AUTHORIZATION_GROUP).withKind(CLUSTER_ROLE).withName(getClusterRoleName(controllerName))
                .build();
    }

    private RoleBinding createRoleBinding(String roleBindingName,
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
                .addNewSubject(null, SERVICE_ACCOUNT, getServiceAccountName(),
                        getNamespace())
                .build();
    }

    private String getServiceAccountName() {
        return serviceAccountName;
    }

    private String getNamespace() {
        return serviceAccountNamespace;
    }

    private ClusterRoleBinding createClusterRoleBinding(
            String controllerName, String bindingName, String controllerConfMessage,
            RoleRef roleRef) {
        outputWarningIfNeeded(controllerName, bindingName, controllerConfMessage);
        roleRef = roleRef == null ? createDefaultRoleRef(controllerName) : roleRef;
        final var ns = getNamespace();
        log.infov("Creating ''{0}'' ClusterRoleBinding to be applied to ''{1}'' namespace", bindingName, ns);
        return new ClusterRoleBindingBuilder()
                .withNewMetadata().withName(bindingName)
                .endMetadata()
                .withRoleRef(roleRef)
                .addNewSubject()
                .withKind(SERVICE_ACCOUNT).withName(getServiceAccountName()).withNamespace(ns)
                .endSubject()
                .build();
    }

    private void outputWarningIfNeeded(String controllerName, String crBindingName, String controllerConfMessage) {
        // the decorator can be called several times but we only want to output the warning once
        if (getNamespace() == null) {
            log.warnv(
                    "''{0}'' controller is configured to "
                            + controllerConfMessage
                            + ", this requires a ClusterRoleBinding for which we MUST specify the namespace of the operator ServiceAccount. This can be specified by setting the ''quarkus.kubernetes.namespace'' property. However, as this property is not set, we are leaving the namespace blank to be provided by the user by editing the ''{1}'' ClusterRoleBinding to provide the namespace in which the operator will be deployed.",
                    controllerName, crBindingName);
        }
    }

    @Override
    public void visit(KubernetesListBuilder list) {
        configs.forEach(config -> {
            final var toAdd = cachedBindings.computeIfAbsent(config, this::bindingsFor);
            list.addAllToItems(toAdd);
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<? extends Decorator>[] after() {
        return new Class[] { AddServiceAccountResourceDecorator.class };
    }

    private String getServiceAccountName(KubernetesListBuilder list) {
        final var items = list.getVisitableMap()
                .map(visitables -> visitables.get("items"))
                .orElseThrow(() -> new IllegalStateException("Items not found in generated resources list"));
        final var deployment = items.stream().filter(visitable -> visitable instanceof DeploymentBuilder)
                .map(DeploymentBuilder.class::cast)
                .map(DeploymentBuilder::build)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Deployment not found in generated resources list"));
        return Optional.ofNullable(deployment.getSpec().getTemplate().getSpec().getServiceAccountName())
                .orElseThrow(() -> new IllegalStateException("Service account name not found in generated resources list"));
    }

    private List<HasMetadata> bindingsFor(QuarkusControllerConfiguration<?> controllerConfiguration) {
        final var controllerName = controllerConfiguration.getName();

        // retrieve which namespaces should be used to generate either from annotation or from the build time configuration
        final var desiredWatchedNamespaces = controllerConfiguration.getNamespaces();

        // if we validate the CRDs, also create a binding for the CRD validating role
        List<HasMetadata> itemsToAdd;
        if (operatorConfiguration.crd().validate()) {
            final var crBindingName = getCRDValidatingBindingName(controllerName);
            final var crdValidatorRoleBinding = createClusterRoleBinding(controllerName,
                    crBindingName, "validate CRDs", CRD_VALIDATING_ROLE_REF);
            itemsToAdd = new ArrayList<>(desiredWatchedNamespaces.size() + 1);
            itemsToAdd.add(crdValidatorRoleBinding);
        } else {
            itemsToAdd = new ArrayList<>(desiredWatchedNamespaces.size());
        }

        final var roleBindingName = getRoleBindingName(controllerName);
        if (controllerConfiguration.watchCurrentNamespace()) {
            // create a RoleBinding that will be applied in the current namespace if watching only the current NS
            itemsToAdd.add(createRoleBinding(roleBindingName, null, createDefaultRoleRef(controllerName)));
            // add additional Role Bindings
            controllerConfiguration.getAdditionalRBACRoleRefs().forEach(
                    roleRef -> {
                        final var specificRoleBindingName = getSpecificRoleBindingName(controllerName, roleRef);
                        itemsToAdd.add(createRoleBinding(specificRoleBindingName, null, roleRef));
                    });
        } else if (controllerConfiguration.watchAllNamespaces()) {
            itemsToAdd.add(createClusterRoleBinding(controllerName,
                    getClusterRoleBindingName(controllerName), "watch all namespaces",
                    null));
            // add additional cluster role bindings only if they target cluster roles
            controllerConfiguration.getAdditionalRBACRoleRefs().forEach(
                    roleRef -> {
                        if (!CLUSTER_ROLE.equals(roleRef.getKind())) {
                            log.warnv("Cannot create a ClusterRoleBinding for RoleRef ''{0}'' because it's not a ClusterRole",
                                    roleRef);
                        } else {
                            itemsToAdd.add(createClusterRoleBinding(controllerName,
                                    roleRef.getName() + "-" + getClusterRoleBindingName(controllerName),
                                    "watch all namespaces", roleRef));
                        }
                    });
        } else {
            // create a RoleBinding using either the provided deployment namespace or the desired watched namespace name
            desiredWatchedNamespaces
                    .forEach(ns -> {
                        itemsToAdd.add(createRoleBinding(roleBindingName, ns, createDefaultRoleRef(controllerName)));
                        //add additional Role Bindings
                        controllerConfiguration.getAdditionalRBACRoleRefs()
                                .forEach(roleRef -> {
                                    final var specificRoleBindingName = getSpecificRoleBindingName(controllerName, roleRef);
                                    itemsToAdd.add(createRoleBinding(specificRoleBindingName, ns, roleRef));
                                });
                    });
        }

        return itemsToAdd;
    }
}
