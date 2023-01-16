package io.quarkiverse.operatorsdk.deployment;

import java.util.ArrayList;
import java.util.Arrays;
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
import io.quarkiverse.operatorsdk.runtime.DependentResourceSpecMetadata;
import io.quarkiverse.operatorsdk.runtime.QuarkusControllerConfiguration;

public class AddClusterRolesDecorator extends ResourceProvidingDecorator<KubernetesListBuilder> {

    public static final String[] READ_VERBS = new String[] { "get", "list", "watch" };
    public static final String[] UPDATE_VERBS = new String[] { "patch", "update" };

    public static final String CREATE_VERB = "create";
    public static final String DELETE_VERB = "delete";
    public static final String[] ALL_VERBS;
    static {
        final var verbs = new ArrayList<String>(READ_VERBS.length + UPDATE_VERBS.length + 2);
        verbs.addAll(Arrays.asList(READ_VERBS));
        verbs.addAll(Arrays.asList(UPDATE_VERBS));
        verbs.add(CREATE_VERB);
        verbs.add(DELETE_VERB);
        ALL_VERBS = verbs.toArray(new String[0]);
    }

    static final String JOSDK_CRD_VALIDATING_CLUSTER_ROLE = "josdk-crd-validating-cluster-role";
    @SuppressWarnings("rawtypes")
    private final Map<String, QuarkusControllerConfiguration> configs;

    private final boolean validateCRDs;

    @SuppressWarnings("rawtypes")
    public AddClusterRolesDecorator(
            Map<String, QuarkusControllerConfiguration> configs, boolean validateCRDs) {
        this.configs = configs;
        this.validateCRDs = validateCRDs;
    }

    @Override
    public void visit(KubernetesListBuilder list) {
        configs.forEach((controller, cri) -> {
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
                    .addToVerbs(ALL_VERBS)
                    .build();

            final var clusterRoleBuilder = new ClusterRoleBuilder()
                    .withNewMetadata()
                    .withName(getClusterRoleName(controller))
                    .endMetadata()
                    .addToRules(rule.build());

            @SuppressWarnings({ "rawtypes", "unchecked" })
            final Map<String, DependentResourceSpecMetadata> dependentsMetadata = cri.getDependentsMetadata();
            dependentsMetadata.forEach((name, spec) -> {
                final var dependentResourceClass = spec.getDependentResourceClass();
                final var associatedResourceClass = spec.getDependentType();

                // only process Kubernetes dependents
                if (HasMetadata.class.isAssignableFrom(associatedResourceClass)) {
                    final var dependentRule = new PolicyRuleBuilder()
                            .addToApiGroups(HasMetadata.getGroup(associatedResourceClass))
                            .addToResources(HasMetadata.getPlural(associatedResourceClass))
                            .addToVerbs(READ_VERBS);
                    if (Creator.class.isAssignableFrom(dependentResourceClass)) {
                        dependentRule.addToVerbs(CREATE_VERB);
                    }
                    if (Updater.class.isAssignableFrom(dependentResourceClass)) {
                        dependentRule.addToVerbs(UPDATE_VERBS);
                    }
                    if (Deleter.class.isAssignableFrom(dependentResourceClass)) {
                        dependentRule.addToVerbs(DELETE_VERB);
                    }
                    clusterRoleBuilder.addToRules(dependentRule.build());
                }

            });

            list.addToItems(clusterRoleBuilder.build());
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

    public static String getClusterRoleName(String controller) {
        return controller + "-cluster-role";
    }
}
