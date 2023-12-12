package io.quarkiverse.operatorsdk.deployment;

import java.util.List;
import java.util.function.BooleanSupplier;

import org.eclipse.microprofile.config.ConfigProvider;

import io.quarkiverse.operatorsdk.common.DeserializedKubernetesResourcesBuildItem;
import io.quarkiverse.operatorsdk.common.GeneratedResourcesUtils;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.kubernetes.spi.GeneratedKubernetesResourceBuildItem;

public class GeneratedKubernetesManifestsProcessor {
    private static class NeedResourcesDeserialization implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            final var helmEnabled = ConfigProvider.getConfig()
                    .getOptionalValue("quarkus.operator-sdk.helm.enabled", Boolean.class).orElse(false);
            final var bundleEnabled = ConfigProvider.getConfig()
                    .getOptionalValue("quarkus.operator-sdk.bundle.enabled", Boolean.class).orElse(false);
            return helmEnabled || bundleEnabled;
        }
    }

    @BuildStep(onlyIf = NeedResourcesDeserialization.class)
    DeserializedKubernetesResourcesBuildItem deserializeGeneratedKubernetesResources(
            List<GeneratedKubernetesResourceBuildItem> generatedResources) {
        return new DeserializedKubernetesResourcesBuildItem(GeneratedResourcesUtils.loadFrom(generatedResources));
    }
}
