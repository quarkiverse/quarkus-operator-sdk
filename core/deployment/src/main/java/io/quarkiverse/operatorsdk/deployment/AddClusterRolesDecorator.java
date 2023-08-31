package io.quarkiverse.operatorsdk.deployment;

import java.util.Collection;
import java.util.Map;

import io.dekorate.kubernetes.decorator.ResourceProvidingDecorator;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBuilder;
import io.fabric8.kubernetes.api.model.rbac.PolicyRuleBuilder;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.Updater;
import io.quarkiverse.operatorsdk.annotations.Verbs;
import io.quarkiverse.operatorsdk.runtime.DependentResourceSpecMetadata;
import io.quarkiverse.operatorsdk.runtime.QuarkusControllerConfiguration;

public class AddClusterRolesDecorator extends ResourceProvidingDecorator<KubernetesListBuilder> {

    static final String JOSDK_CRD_VALIDATING_CLUSTER_ROLE = "josdk-crd-validating-cluster-role";
    @SuppressWarnings("rawtypes")
    private final Collection<QuarkusControllerConfiguration> configs;

    private final boolean validateCRDs;

    @SuppressWarnings("rawtypes")
    public AddClusterRolesDecorator(Collection<QuarkusControllerConfiguration> configs, boolean validateCRDs) {
        this.configs = configs;
        this.validateCRDs = validateCRDs;
    }

    @Override
    public void visit(KubernetesListBuilder list) {
        configs.forEach(cri -> {
            var clusterRole = createClusterRole(cri);
            list.addToItems(clusterRole);
        });

        // if we're asking to validate the CRDs, also add CRDs permissions, once
        if (validateCRDs) {
            final var crName = JOSDK_CRD_VALIDATING_CLUSTER_ROLE;

            if (!contains(list, HasMetadata.getApiVersion(ClusterRole.class), HasMetadata.getKind(ClusterRole.class), crName)) {
                list.addToItems(new ClusterRoleBuilder().withNewMetadata().withName(crName).endMetadata()
                        .addToRules(new PolicyRuleBuilder()
                                .addToApiGroups("apiextensions.k8s.io")
                                .addToResources("customresourcedefinitions")
                                .addToVerbs("get", "list")
                                .build()));
            }
        }
    }

    public static ClusterRole createClusterRole(QuarkusControllerConfiguration<?> cri) {
        final var rule = new PolicyRuleBuilder();
        final var resourceClass = cri.getResourceClass();
        final var plural = HasMetadata.getPlural(resourceClass);
        rule.addToResources(plural);

        // if the resource has a non-Void status, also add the status resource
        if (cri.isStatusPresentAndNotVoid()) {
            rule.addToResources(plural + "/status");
        }

        // add finalizers sub-resource because it's used in several contexts, even in the absence of finalizers
        // see: https://kubernetes.io/docs/reference/access-authn-authz/admission-controllers/#ownerreferencespermissionenforcement
        rule.addToResources(plural + "/finalizers");

        rule.addToApiGroups(HasMetadata.getGroup(resourceClass))
                .addToVerbs(Verbs.ALL_COMMON_VERBS)
                .build();

        final var clusterRoleBuilder = new ClusterRoleBuilder()
                .withNewMetadata()
                .withName(getClusterRoleName(cri.getName()))
                .endMetadata()
                .addToRules(rule.build());

        final Map<String, DependentResourceSpecMetadata<?, ?, ?>> dependentsMetadata = cri.getDependentsMetadata();
        dependentsMetadata.forEach((name, spec) -> {
            final var dependentResourceClass = spec.getDependentResourceClass();
            final var associatedResourceClass = spec.getDependentType();

            // only process Kubernetes dependents
            if (HasMetadata.class.isAssignableFrom(associatedResourceClass)) {
                final var dependentRule = new PolicyRuleBuilder()
                        .addToApiGroups(HasMetadata.getGroup(associatedResourceClass))
                        .addToResources(HasMetadata.getPlural(associatedResourceClass))
                        .addToVerbs(Verbs.READ_VERBS);
                if (Updater.class.isAssignableFrom(dependentResourceClass)) {
                    dependentRule.addToVerbs(Verbs.UPDATE_VERBS);
                }
                if (Deleter.class.isAssignableFrom(dependentResourceClass)) {
                    dependentRule.addToVerbs(Verbs.DELETE);
                }
                if (Creator.class.isAssignableFrom(dependentResourceClass)) {
                    dependentRule.addToVerbs(Verbs.CREATE);
                    if (!dependentRule.getVerbs().contains(Verbs.PATCH)) {
                        dependentRule.addToVerbs(Verbs.PATCH);
                    }
                }
                clusterRoleBuilder.addToRules(dependentRule.build());
            }

        });
        return clusterRoleBuilder.build();
    }

    public static String getClusterRoleName(String controller) {
        return controller + "-cluster-role";
    }
}
