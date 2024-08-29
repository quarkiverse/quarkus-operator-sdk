package io.quarkiverse.operatorsdk.deployment;

import java.util.function.BooleanSupplier;

import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.quarkiverse.operatorsdk.runtime.BuildTimeOperatorConfiguration;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Produce;
import io.quarkus.deployment.builditem.ApplicationInfoBuildItem;
import io.quarkus.deployment.pkg.builditem.ArtifactResultBuildItem;
import io.quarkus.kubernetes.deployment.KubernetesConfig;
import io.quarkus.kubernetes.deployment.ResourceNameUtil;
import io.quarkus.kubernetes.spi.KubernetesClusterRoleBindingBuildItem;
import io.quarkus.kubernetes.spi.KubernetesClusterRoleBuildItem;
import io.quarkus.kubernetes.spi.KubernetesRoleBindingBuildItem;
import io.quarkus.kubernetes.spi.PolicyRule;
import io.quarkus.kubernetes.spi.RoleRef;
import io.quarkus.kubernetes.spi.Subject;

public class RBACAugmentationStep {
    private static final String ANY_TARGET = null;

    private static class IsRBACEnabled implements BooleanSupplier {

        private BuildTimeOperatorConfiguration config;

        @Override
        public boolean getAsBoolean() {
            return !config.disableRbacGeneration();
        }
    }

    @BuildStep(onlyIf = IsRBACEnabled.class)
    @Produce(ArtifactResultBuildItem.class)
    void augmentRBACForResources(BuildTimeOperatorConfiguration buildTimeConfiguration, KubernetesConfig kubernetesConfig,
            BuildProducer<KubernetesClusterRoleBuildItem> clusterRolesProducer,
            BuildProducer<KubernetesRoleBindingBuildItem> roleBindingsProducer,
            BuildProducer<KubernetesClusterRoleBindingBuildItem> clusterRoleBindingsProducer,
            ApplicationInfoBuildItem applicationInfo,
            ControllerConfigurationsBuildItem configurations) {

        final var configs = configurations.getControllerConfigs().values();
        AddClusterRolesDecorator.createClusterRoles(configs, buildTimeConfiguration.crd().validate())
                .forEach(clusterRole -> clusterRolesProducer.produce(clusterRoleBuildItemFrom(clusterRole)));

        final var defaultServiceAccountName = ResourceNameUtil.getResourceName(kubernetesConfig, applicationInfo);
        final var serviceAccountName = kubernetesConfig.getServiceAccount().orElse(defaultServiceAccountName);
        AddRoleBindingsDecorator.createRoleBindings(configs, buildTimeConfiguration, serviceAccountName)
                .forEach(binding -> roleBindingsProducer.produce(roleBindingItemFor(binding)));
        AddRoleBindingsDecorator.createClusterRoleBindings(configs, buildTimeConfiguration, serviceAccountName)
                .forEach(binding -> clusterRoleBindingsProducer.produce(clusterRoleBindingFor(binding)));
    }

    private KubernetesRoleBindingBuildItem roleBindingItemFor(RoleBinding binding) {
        final var roleRef = convertToQuarkusRoleRef(binding.getRoleRef(), false); // todo: fix
        final var subjects = binding.getSubjects().stream()
                .map(RBACAugmentationStep::convertToQuarkusSubject)
                .toArray(Subject[]::new);

        return new KubernetesRoleBindingBuildItem(binding.getMetadata().getName(), binding.getMetadata().getNamespace(),
                RBACAugmentationStep.ANY_TARGET,
                binding.getMetadata().getLabels(), roleRef, subjects);
    }

    private KubernetesClusterRoleBindingBuildItem clusterRoleBindingFor(ClusterRoleBinding binding) {
        final var roleRef = convertToQuarkusRoleRef(binding.getRoleRef(), true); // todo: fix
        final var subjects = binding.getSubjects().stream()
                .map(RBACAugmentationStep::convertToQuarkusSubject)
                .toArray(Subject[]::new);

        return new KubernetesClusterRoleBindingBuildItem(binding.getMetadata().getName(), RBACAugmentationStep.ANY_TARGET,
                binding.getMetadata().getLabels(), roleRef, subjects);
    }

    private static Subject convertToQuarkusSubject(io.fabric8.kubernetes.api.model.rbac.Subject subject) {
        return new Subject(subject.getApiGroup(), subject.getKind(), subject.getName(), subject.getNamespace());
    }

    private static RoleRef convertToQuarkusRoleRef(io.fabric8.kubernetes.api.model.rbac.RoleRef roleRef, boolean clusterWide) {
        return new RoleRef(roleRef.getName(), clusterWide);
    }

    private static KubernetesClusterRoleBuildItem clusterRoleBuildItemFrom(ClusterRole clusterRole) {
        return new KubernetesClusterRoleBuildItem(clusterRole.getMetadata().getName(),
                clusterRole.getRules().stream().map(RBACAugmentationStep::convertToQuarkusPolicyRule).toList(), ANY_TARGET);
    }

    private static PolicyRule convertToQuarkusPolicyRule(io.fabric8.kubernetes.api.model.rbac.PolicyRule pr) {
        return new PolicyRule(pr.getApiGroups(), pr.getNonResourceURLs(), pr.getResourceNames(), pr.getResources(),
                pr.getVerbs());
    }
}
