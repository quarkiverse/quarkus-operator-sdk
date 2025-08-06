package io.quarkiverse.operatorsdk.deployment.helm;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.jboss.logging.Logger;

import io.quarkiverse.helm.deployment.HelmChartConfig;
import io.quarkiverse.helm.deployment.HelmEnabled;
import io.quarkiverse.helm.spi.CustomHelmOutputDirBuildItem;
import io.quarkiverse.helm.spi.HelmChartBuildItem;
import io.quarkiverse.operatorsdk.common.FileUtils;
import io.quarkiverse.operatorsdk.deployment.GeneratedCRDInfoBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.annotations.Produce;
import io.quarkus.deployment.pkg.builditem.ArtifactResultBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;

// Note that steps of this processor won't be run in dev mode because ArtifactResultBuildItems are only considered in NORMAL mode
@BuildSteps(onlyIf = HelmEnabled.class)
public class HelmChartProcessor {

    private static final Logger log = Logger.getLogger(HelmChartProcessor.class);

    //Added line
    static final String TEMPLATES_DIR = "templates";
    public static final String CRD_DIR = "crds";

    @BuildStep
    void produceHelmTargetDirectories(
            BuildProducer<HelmTargetDirectoryBuildItem> helmTargetDirectoryBuildItemBuildProducer,
            List<HelmChartBuildItem> helmChartBuildItems,
            HelmChartConfig helmChartConfig,
            @SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<CustomHelmOutputDirBuildItem> customHelmOutputDirBuildItem,
            OutputTargetBuildItem outputTargetBuildItem) {
        log.info("Generating QOSDK Helm resources for the following Helm charts - " + helmChartBuildItems);
        final var helmOutputDirectory = customHelmOutputDirBuildItem
                .map(CustomHelmOutputDirBuildItem::getOutputDir)
                .orElse(outputTargetBuildItem.getOutputDirectory().resolve(helmChartConfig.outputDirectory()));
        helmChartBuildItems.forEach(helmChartBuildItem -> {
            final var helmTargetDirectory = helmOutputDirectory
                    .resolve(helmChartBuildItem.getDeploymentTarget())
                    .resolve(helmChartBuildItem.getName());
            helmTargetDirectoryBuildItemBuildProducer.produce(new HelmTargetDirectoryBuildItem(helmTargetDirectory.toFile()));
        });
    }

    @BuildStep
    @Produce(ArtifactResultBuildItem.class)
    private void addCRDs(List<HelmTargetDirectoryBuildItem> helmTargetDirectories,
            GeneratedCRDInfoBuildItem generatedCRDInfoBuildItem) {
        var crdInfos = generatedCRDInfoBuildItem.getCRDGenerationInfo().getCrds().getCRDNameToInfoMappings().values();

        helmTargetDirectories.forEach(helmTargetDirectory -> {
            final var crdDir = helmTargetDirectory.getPathToHelmDir().resolve(CRD_DIR);
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
