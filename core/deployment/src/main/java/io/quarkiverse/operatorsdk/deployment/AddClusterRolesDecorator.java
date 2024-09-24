package io.quarkiverse.operatorsdk.deployment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.dekorate.kubernetes.decorator.ResourceProvidingDecorator;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBuilder;
import io.fabric8.kubernetes.api.model.rbac.PolicyRule;
import io.fabric8.kubernetes.api.model.rbac.PolicyRuleBuilder;
import io.javaoperatorsdk.operator.api.config.Utils;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.Updater;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.GenericKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig;
import io.quarkiverse.operatorsdk.annotations.RBACVerbs;
import io.quarkiverse.operatorsdk.runtime.DependentResourceSpecMetadata;
import io.quarkiverse.operatorsdk.runtime.QuarkusControllerConfiguration;

public class AddClusterRolesDecorator extends ResourceProvidingDecorator<KubernetesListBuilder> {

    public static final String JOSDK_CRD_VALIDATING_CLUSTER_ROLE_NAME = "josdk-crd-validating-cluster-role";
    private static final ClusterRoleBuilder CRD_VALIDATING_CLUSTER_ROLE_BUILDER = new ClusterRoleBuilder().withNewMetadata()
            .withName(JOSDK_CRD_VALIDATING_CLUSTER_ROLE_NAME).endMetadata()
            .addToRules(new PolicyRuleBuilder()
                    .addToApiGroups("apiextensions.k8s.io")
                    .addToResources("customresourcedefinitions")
                    .addToVerbs("get", "list")
                    .build());
    private static final String CR_API_VERSION = HasMetadata.getApiVersion(ClusterRole.class);
    private static final String CR_KIND = HasMetadata.getKind(ClusterRole.class);
    private static final Logger log = Logger.getLogger(AddClusterRolesDecorator.class);
    private static final String ADD_CLUSTER_ROLES_DECORATOR = "AddClusterRolesDecorator";
    private final Collection<QuarkusControllerConfiguration<?>> configs;

    private final boolean validateCRDs;

    public AddClusterRolesDecorator(Collection<QuarkusControllerConfiguration<?>> configs, boolean validateCRDs) {
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
            if (!contains(list, CR_API_VERSION, CR_KIND, JOSDK_CRD_VALIDATING_CLUSTER_ROLE_NAME)) {
                list.addToItems(CRD_VALIDATING_CLUSTER_ROLE_BUILDER);
            }
        }
    }

    public static ClusterRole createClusterRole(QuarkusControllerConfiguration<?> cri) {
        final var rules = new LinkedHashMap<String, PolicyRule>();
        final var clusterRolePolicyRuleFromPrimaryResource = getClusterRolePolicyRuleFromPrimaryResource(cri);
        final var primaryRuleKey = getKeyFor(clusterRolePolicyRuleFromPrimaryResource);
        rules.put(primaryRuleKey, clusterRolePolicyRuleFromPrimaryResource);

        collectAndMergeIfNeededRulesFrom(getClusterRolePolicyRulesFromDependentResources(cri), rules);
        collectAndMergeIfNeededRulesFrom(cri.getAdditionalRBACRules(), rules);

        return new ClusterRoleBuilder()
                .withNewMetadata()
                .withName(getClusterRoleName(cri.getName()))
                .endMetadata()
                .addAllToRules(rules.values())
                .build();
    }

    private static void collectAndMergeIfNeededRulesFrom(Collection<PolicyRule> newRules,
            Map<String, PolicyRule> existingRules) {
        newRules.forEach(newPolicyRule -> {
            final var key = getKeyFor(newPolicyRule);
            existingRules.merge(key, newPolicyRule, (existing, npr) -> {
                Set<String> verbs1 = new TreeSet<>(existing.getVerbs());
                verbs1.addAll(npr.getVerbs());
                existing.setVerbs(new ArrayList<>(verbs1));
                return existing;
            });
        });
    }

    private static String getKeyFor(PolicyRule rule) {
        return rule.getApiGroups().stream().sorted().collect(Collectors.joining("-")) + "/"
                + rule.getResources().stream().sorted().collect(Collectors.joining("-"));
    }

    @SuppressWarnings("unchecked")
    private static Set<PolicyRule> getClusterRolePolicyRulesFromDependentResources(QuarkusControllerConfiguration<?> cri) {
        Set<PolicyRule> rules = new LinkedHashSet<>();
        final Map<String, DependentResourceSpecMetadata<?, ?, ?>> dependentsMetadata = cri.getDependentsMetadata();
        dependentsMetadata.forEach((name, spec) -> {
            final var dependentResourceClass = spec.getDependentResourceClass();
            final var associatedResourceClass = spec.getDependentType();

            // only process Kubernetes dependents
            if (HasMetadata.class.isAssignableFrom(associatedResourceClass)) {
                var resourceGroup = HasMetadata.getGroup(associatedResourceClass);
                var resourcePlural = HasMetadata.getPlural(associatedResourceClass);

                final var verbs = new TreeSet<>(List.of(RBACVerbs.READ_VERBS));
                if (Updater.class.isAssignableFrom(dependentResourceClass)) {
                    verbs.addAll(List.of(RBACVerbs.UPDATE_VERBS));
                }
                if (Deleter.class.isAssignableFrom(dependentResourceClass)) {
                    verbs.add(RBACVerbs.DELETE);
                }
                final var isCreator = Creator.class.isAssignableFrom(dependentResourceClass);
                boolean shouldDoubleCheckPatch = false;
                if (isCreator) {
                    verbs.add(RBACVerbs.CREATE);

                    // PATCH verb is also needed when using SSA to be able to add the finalizer when creating the resource
                    // Here, we optimistically add PATCH method if the resource configuration states that SSA should be
                    // used, despite this not being a correct/complete determination of whether the resource actually
                    // uses SSA. This can only be determined by instantiating the dependent, which is why, if we can
                    // instantiate it, we double-check the SSA status later on and remove the PATCH method if we can
                    // actually determine that it's not needed
                    final Object dependentResourceConfig = spec.getDependentResourceConfig();
                    if (dependentResourceConfig instanceof KubernetesDependentResourceConfig<?> kubernetesDependentResourceConfig) {
                        if (kubernetesDependentResourceConfig.useSSA().orElse(false)) {
                            verbs.add(RBACVerbs.PATCH);
                            shouldDoubleCheckPatch = true;
                        }
                    }
                }

                // Check if we're dealing with typeless Kubernetes resource or if we need to deal with SSA
                boolean ignore = false;
                KubernetesDependentResource<?, ?> kubeResource = null;
                if (KubernetesDependentResource.class.isAssignableFrom(dependentResourceClass)) {
                    try {
                        //noinspection rawtypes
                        kubeResource = Utils.instantiate(
                                (Class<? extends KubernetesDependentResource>) dependentResourceClass,
                                KubernetesDependentResource.class, ADD_CLUSTER_ROLES_DECORATOR);

                        if (kubeResource instanceof GenericKubernetesDependentResource<? extends HasMetadata> genericKubeRes) {
                            final var gvk = genericKubeRes.getGroupVersionKind();
                            resourceGroup = gvk.getGroup();
                            resourcePlural = gvk.getPluralOrDefault();
                        }
                    } catch (Exception e) {
                        ignore = true;
                        log.warn(" Ignoring dependent " + dependentResourceClass.getName()
                                + " because it couldn't be instantiated as it doesn't provide a no-arg constructor, preventing its group and plural to be determined.");
                    }
                }

                if (!ignore) {
                    // if we need to double check if we really should use SSA
                    if (shouldDoubleCheckPatch) {
                        // we can only check if we managed to instantiate the dependent, though
                        if (kubeResource != null) {
                            if (!cri.getConfigurationService().shouldUseSSA(kubeResource)) {
                                verbs.remove(RBACVerbs.PATCH);
                            }
                        } else {
                            // if we couldn't double check, warn the user
                            log.warn("Couldn't verify that dependent " + dependentResourceClass.getName()
                                    + " really needs PATCH permission for SSA because it couldn't be instantiated. This means that a PATCH verb might have been added to the rule (group: "
                                    + resourceGroup + " / plural: " + resourcePlural + ") when not needed.");
                        }
                    }

                    final var dependentRule = new PolicyRuleBuilder()
                            .addToApiGroups(resourceGroup)
                            .addToResources(resourcePlural);
                    dependentRule.addToVerbs(verbs.toArray(String[]::new));
                    rules.add(dependentRule.build());
                }
            }
        });
        return rules;
    }

    private static PolicyRule getClusterRolePolicyRuleFromPrimaryResource(QuarkusControllerConfiguration<?> cri) {
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
                .addToVerbs(RBACVerbs.ALL_COMMON_VERBS)
                .build();
        return rule.build();
    }

    public static String getClusterRoleName(String controller) {
        return controller + "-cluster-role";
    }
}
