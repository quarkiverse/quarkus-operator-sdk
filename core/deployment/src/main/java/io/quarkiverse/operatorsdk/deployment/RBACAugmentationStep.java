package io.quarkiverse.operatorsdk.deployment;

import java.util.function.BooleanSupplier;

import io.quarkiverse.operatorsdk.runtime.BuildTimeOperatorConfiguration;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.kubernetes.spi.DecoratorBuildItem;

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
            BuildProducer<DecoratorBuildItem> decorators,
            ControllerConfigurationsBuildItem configurations) {

        final var configs = configurations.getControllerConfigs().values();
        decorators.produce(new DecoratorBuildItem(
                new AddClusterRolesDecorator(configs, buildTimeConfiguration.crd().validate())));
        decorators.produce(new DecoratorBuildItem(
                new AddRoleBindingsDecorator(configs, buildTimeConfiguration)));
    }
}
