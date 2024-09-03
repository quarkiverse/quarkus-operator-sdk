package io.quarkiverse.operatorsdk.deployment;

import io.quarkiverse.operatorsdk.runtime.BuildTimeConfigurationService;
import io.quarkiverse.operatorsdk.runtime.BuildTimeOperatorConfiguration;
import io.quarkus.deployment.annotations.BuildStep;

public class BuildTimeConfigurationServiceBuildStep {

    @BuildStep
    BuildTimeConfigurationServiceBuildItem createBuildTimeConfigurationService(VersionBuildItem versionBuildItem,
            GeneratedCRDInfoBuildItem generatedCRDs, BuildTimeOperatorConfiguration buildTimeConfig) {
        final var service = new BuildTimeConfigurationService(
                versionBuildItem.getVersion(),
                generatedCRDs.getCRDGenerationInfo(),
                buildTimeConfig.startOperator(),
                buildTimeConfig.closeClientOnStop(),
                buildTimeConfig.stopOnInformerErrorDuringStartup(),
                buildTimeConfig.enableSSA(),
                buildTimeConfig.activateLeaderElectionForProfiles(),
                buildTimeConfig.defensiveCloning());
        return new BuildTimeConfigurationServiceBuildItem(service);
    }
}
