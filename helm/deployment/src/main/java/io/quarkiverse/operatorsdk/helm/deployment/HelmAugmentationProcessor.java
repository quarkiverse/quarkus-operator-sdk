package io.quarkiverse.operatorsdk.helm.deployment;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.jboss.logging.Logger;

import io.quarkiverse.helm.deployment.HelmChartConfig;
import io.quarkiverse.helm.spi.CustomHelmOutputDirBuildItem;
import io.quarkiverse.helm.spi.HelmChartBuildItem;
import io.quarkiverse.operatorsdk.common.FileUtils;
import io.quarkiverse.operatorsdk.deployment.GeneratedCRDInfoBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Produce;
import io.quarkus.deployment.pkg.builditem.ArtifactResultBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;

/**
 * This processor copies the generated CRDs into the Helm chart output directories.
 * <p>
 * It is required to be in a separate extension because we want to include the Helm
 * processing conditionally, based on the presence of the quarkus-helm extension. If
 * the user includes the quarkus-helm extension, this processor will be executed
 * and the CRDs will be copied into the Helm chart output directories. See the
 * quarkus-extension-maven-plugin configuration in the runtime modules of this and
 * the operator-sdk core extensions where this is configured.
 */
public class HelmAugmentationProcessor {

    private static final Logger log = Logger.getLogger(HelmAugmentationProcessor.class);

    public static final String CRD_DIR = "crds";

    @BuildStep
    @Produce(ArtifactResultBuildItem.class)
    void addCRDsToHelmChart(
            List<HelmChartBuildItem> helmChartBuildItems,
            HelmChartConfig helmChartConfig,
            @SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<CustomHelmOutputDirBuildItem> customHelmOutputDirBuildItem,
            OutputTargetBuildItem outputTargetBuildItem,
            GeneratedCRDInfoBuildItem generatedCRDInfoBuildItem) {
        log.info("Generating QOSDK Helm resources for the following Helm charts - " + helmChartBuildItems);

        final var helmOutputDirectory = customHelmOutputDirBuildItem
                .map(CustomHelmOutputDirBuildItem::getOutputDir)
                .orElse(outputTargetBuildItem.getOutputDirectory().resolve(helmChartConfig.outputDirectory()));

        var crdInfos = generatedCRDInfoBuildItem.getCRDGenerationInfo().getCrds().getCRDNameToInfoMappings().values();

        helmChartBuildItems.forEach(helmChartBuildItem -> {
            final var crdDir = helmOutputDirectory
                    .resolve(helmChartBuildItem.getDeploymentTarget())
                    .resolve(helmChartBuildItem.getName())
                    .resolve(CRD_DIR);

            FileUtils.ensureDirectoryExists(crdDir.toFile());
            crdInfos.forEach(crdInfo -> {
                try {
                    var generateCrdPath = Path.of(crdInfo.getFilePath());
                    // replace needed since tests might generate files multiple times
                    Files.copy(generateCrdPath, crdDir.resolve(generateCrdPath.getFileName().toString()), REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
        });
    }
}
