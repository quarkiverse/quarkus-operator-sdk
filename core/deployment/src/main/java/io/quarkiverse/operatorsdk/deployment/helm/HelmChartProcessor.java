package io.quarkiverse.operatorsdk.deployment.helm;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;

import org.jboss.logging.Logger;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.pkg.builditem.ArtifactResultBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.kubernetes.spi.GeneratedKubernetesResourceBuildItem;

public class HelmChartProcessor {

    private static final Logger log = Logger.getLogger(HelmChartProcessor.class);

    private static final String TEMPLATES_DIR = "templates";
    private static final String[] TEMPLATE_FILES = new String[] {
            "crd-cluster-role.yaml",
            "crd-role-bindings.yaml",
            "deployment.yaml",
            "generic-crd-cluster-role.yaml",
            "generic-crd-cluster-role-binding.yaml",
            "service.yaml",
            "serviceaccount.yaml"
    };

    @BuildStep
    public void handleHelmCharts(BuildProducer<ArtifactResultBuildItem> dummy,
            OutputTargetBuildItem outputTarget,
            List<GeneratedKubernetesResourceBuildItem> generatedResources) {
        //todo make configurable
        var helmDir = outputTarget.getOutputDirectory().resolve("helm").toFile();
        log.infov("Generating helm chart to dir");

        createDirIfNotExists(helmDir);
        createDirIfNotExists(new File(helmDir, TEMPLATES_DIR));
        copyTemplatesFolder(helmDir);
        addChartYaml(helmDir);
    }

    private void addChartYaml(File helmDir) {

    }

    private void copyTemplatesFolder(File helmDir) {
        for (String template : TEMPLATE_FILES) {
            try (InputStream file = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream("/helm/templates/" + template)) {
                Files.copy(file, new File(new File(helmDir, TEMPLATES_DIR), template).toPath());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void createDirIfNotExists(File dir) {
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new IllegalArgumentException("Couldn't create " + dir.getAbsolutePath());
            }
        }
    }
}
