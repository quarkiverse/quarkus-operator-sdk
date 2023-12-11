package io.quarkiverse.operatorsdk.deployment.helm;

import static io.quarkiverse.operatorsdk.deployment.helm.HelmChartProcessor.TEMPLATES_DIR;

import java.io.File;
import java.nio.file.Path;

import io.quarkus.builder.item.SimpleBuildItem;

final class HelmTargetDirectoryBuildItem extends SimpleBuildItem {
    private final Path helmDir;
    private final Path templatesDir;

    HelmTargetDirectoryBuildItem(File helmDir) {
        this.helmDir = helmDir.toPath();
        this.templatesDir = Path.of(helmDir.getPath(), TEMPLATES_DIR);
    }

    public File getHelmDir() {
        return helmDir.toFile();
    }

    public Path getPathToHelmDir() {
        return helmDir;
    }

    public Path getPathToTemplatesDir() {
        return templatesDir;
    }
}
