package io.quarkiverse.operatorsdk.deployment;

import java.util.List;
import java.util.function.BooleanSupplier;

import io.quarkiverse.operatorsdk.runtime.BuildTimeOperatorConfiguration;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.kubernetes.spi.DecoratorBuildItem;
import io.quarkus.kubernetes.spi.KubernetesEffectiveServiceAccountBuildItem;

public class RBACAugmentationStep {

    private static class IsRBACEnabled implements BooleanSupplier {

        private BuildTimeOperatorConfiguration config;

        @Override
        public boolean getAsBoolean() {
            return !config.disableRbacGeneration();
        }
    }

    @BuildStep(onlyIf = IsRBACEnabled.class)
    void augmentRBACForResources(BuildTimeOperatorConfiguration buildTimeConfiguration,
            List<KubernetesEffectiveServiceAccountBuildItem> effectiveServiceAccounts,
            BuildProducer<DecoratorBuildItem> decorators,
            ControllerConfigurationsBuildItem configurations) {
        final var effectiveServiceAccount = effectiveServiceAccounts.get(0); // todo: fix
        final var configs = configurations.getControllerConfigs().values();
        decorators.produce(new DecoratorBuildItem(
                new AddClusterRolesDecorator(configs, buildTimeConfiguration.crd().validate())));
        decorators.produce(new DecoratorBuildItem(
                new AddRoleBindingsDecorator(configs, buildTimeConfiguration, effectiveServiceAccount.getServiceAccountName(),
                        effectiveServiceAccount.getNamespace())));
    }
}
