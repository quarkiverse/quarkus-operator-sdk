package io.quarkiverse.operatorsdk.deployment.helm;

import static io.quarkiverse.operatorsdk.deployment.helm.HelmChartProcessor.TEMPLATES_DIR;

import java.io.File;
import java.nio.file.Path;

import io.quarkus.builder.item.MultiBuildItem;

final class HelmTargetDirectoryBuildItem extends MultiBuildItem {
    private final Path helmDir;

    HelmTargetDirectoryBuildItem(File helmDir) {
        this.helmDir = helmDir.toPath();
    }

    public Path getPathToHelmDir() {
        return helmDir;
    }

    public Path getPathToTemplatesDir() {
        return helmDir.resolve(TEMPLATES_DIR);
    }
}
