package io.quarkiverse.operatorsdk.deployment.helm;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.dekorate.helm.model.Chart;
import io.dekorate.utils.Serialization;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.model.annotation.Group;
import io.quarkiverse.operatorsdk.deployment.ReconcilerInfosBuildItem;
import io.quarkus.container.spi.ContainerImageInfoBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.ApplicationInfoBuildItem;
import io.quarkus.deployment.pkg.builditem.ArtifactResultBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.kubernetes.spi.ConfiguratorBuildItem;

// TODO
// - configure:
//    - to generate additional crds?
//
// - add crd-s
// - generate readme with values
// - add various customization options
// - generate the reconciler parts directly into templates
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
    public static final String CHART_YAML_FILENAME = "Chart.yaml";
    public static final String VALUES_YAML_FILENAME = "values.yaml";

    @BuildStep
    public void handleHelmCharts(BuildProducer<ArtifactResultBuildItem> dummy,
            OutputTargetBuildItem outputTarget,
            ApplicationInfoBuildItem appInfo,
            ContainerImageInfoBuildItem containerImageInfoBuildItem,
            ReconcilerInfosBuildItem reconcilerInfosBuildItem) {

        var helmDir = outputTarget.getOutputDirectory().resolve("helm").toFile();
        log.infov("Generating helm chart to dir");

        createDirIfNotExists(helmDir);
        createDirIfNotExists(new File(helmDir, TEMPLATES_DIR));
        copyTemplatesFolder(helmDir);
        addChartYaml(helmDir, appInfo.getName(), appInfo.getVersion());
        addValuesYaml(helmDir, reconcilerInfosBuildItem, containerImageInfoBuildItem.getImage(),
                containerImageInfoBuildItem.getTag());
    }

    private void addValuesYaml(File helmDir,
            ReconcilerInfosBuildItem reconcilerInfosBuildItem,
            String image,
            String tag) {
        try {
            var values = new Values();
            values.setVersion(tag);
            var imageWithoutTage = image.replace(":" + tag, "");
            values.setImage(imageWithoutTage);
            var reconcilerValues = createReconcilerValues(reconcilerInfosBuildItem);
            values.setReconcilers(reconcilerValues);

            var valuesYaml = Serialization.asYaml(values);
            Files.writeString(Path.of(helmDir.getPath(), VALUES_YAML_FILENAME), valuesYaml);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<ReconcilerValues> createReconcilerValues(ReconcilerInfosBuildItem reconcilerInfosBuildItem) {
        return reconcilerInfosBuildItem.getReconcilers().entrySet().stream().map(e -> {
            ReconcilerValues val = new ReconcilerValues();
            val.setApiGroup(e.getValue().associatedResourceInfo()
                    .classInfo().annotation(Group.class).value().value().toString());
            // todo is this correct
            val.setResource(HasMetadata.getPlural(e.getValue().associatedResourceInfo().loadAssociatedClass()));
            val.setName(e.getKey());
            return val;
        }).collect(Collectors.toList());
    }

    private void addChartYaml(File helmDir, String name, String version) {
        try {
            Chart chart = new Chart();
            chart.setName(name);
            chart.setVersion(version);
            chart.setApiVersion("v2");
            var chartYaml = Serialization.asYaml(chart);
            Files.writeString(Path.of(helmDir.getPath(), CHART_YAML_FILENAME), chartYaml);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void copyTemplatesFolder(File helmDir) {
        for (String template : TEMPLATE_FILES) {
            try (InputStream file = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream("/helm/templates/" + template)) {
                Files.copy(file, new File(new File(helmDir, TEMPLATES_DIR), template).toPath());
            } catch (IOException e) {
                throw new IllegalStateException(e);
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

    @BuildStep
    void disableDefaultHelmListener(BuildProducer<ConfiguratorBuildItem> helmConfiguration) {
        helmConfiguration.produce(new ConfiguratorBuildItem(new DisableDefaultHelmListener()));
    }
}
