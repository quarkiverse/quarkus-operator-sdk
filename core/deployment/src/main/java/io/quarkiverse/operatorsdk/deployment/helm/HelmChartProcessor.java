package io.quarkiverse.operatorsdk.deployment.helm;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.dekorate.helm.model.Chart;
import io.dekorate.utils.Serialization;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.PolicyRule;
import io.fabric8.kubernetes.model.annotation.Group;
import io.quarkiverse.operatorsdk.deployment.GeneratedCRDInfoBuildItem;
import io.quarkiverse.operatorsdk.deployment.ReconcilerInfosBuildItem;
import io.quarkiverse.operatorsdk.runtime.BuildTimeOperatorConfiguration;
import io.quarkus.container.spi.ContainerImageInfoBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.ApplicationInfoBuildItem;
import io.quarkus.deployment.pkg.builditem.ArtifactResultBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.kubernetes.spi.ConfiguratorBuildItem;
import io.quarkus.qute.*;

public class HelmChartProcessor {

    private static final Logger log = Logger.getLogger(HelmChartProcessor.class);

    private static final String TEMPLATES_DIR = "templates";
    private static final String[] TEMPLATE_FILES = new String[] {
            "deployment.yaml",
            "generic-crd-cluster-role.yaml",
            "generic-crd-cluster-role-binding.yaml",
            "service.yaml",
            "serviceaccount.yaml"
    };
    public static final String CHART_YAML_FILENAME = "Chart.yaml";
    public static final String VALUES_YAML_FILENAME = "values.yaml";
    public static final String CRD_DIR = "crds";
    public static final String CLUSTER_ROLE_NAME_SUFFIX = "-cluster-role";
    public static final List<String> CRD_ROLE_VERBS = List.of("get", "list", "watch", "patch",
            "update", "create", "delete");

    @BuildStep
    public void handleHelmCharts(
            BuildProducer<ArtifactResultBuildItem> dummy,
            BuildTimeOperatorConfiguration buildTimeConfiguration,
            GeneratedCRDInfoBuildItem generatedCRDInfoBuildItem,
            OutputTargetBuildItem outputTarget,
            ApplicationInfoBuildItem appInfo,
            ContainerImageInfoBuildItem containerImageInfoBuildItem,
            ReconcilerInfosBuildItem reconcilerInfosBuildItem) {

        if (buildTimeConfiguration.helm.enabled) {
            log.infov("Generating helm chart");
            var helmDir = outputTarget.getOutputDirectory().resolve("helm").toFile();
            var reconcilerValues = createReconcilerValues(reconcilerInfosBuildItem);

            createRelatedDirectories(helmDir);
            copyTemplates(helmDir);
            addClusterRolesForReconcilerPrimaries(helmDir, reconcilerValues);
            addPrimaryClusterRoleBindings(helmDir, reconcilerValues);
            addChartYaml(helmDir, appInfo.getName(), appInfo.getVersion());
            addValuesYaml(helmDir, containerImageInfoBuildItem.getImage(),
                    containerImageInfoBuildItem.getTag());
            addCRDs(new File(helmDir, CRD_DIR), generatedCRDInfoBuildItem);
        } else {
            log.infov("Generating helm chart is disabled");
        }
    }

    private void addPrimaryClusterRoleBindings(File helmDir, List<ReconcilerDescriptor> reconcilerValues) {
        try (InputStream file = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("/helm/crd-role-binding-template.yaml")) {
            String template = new String(file.readAllBytes(), StandardCharsets.UTF_8);
            reconcilerValues.forEach(r -> {
                try {
                    String res = Qute.fmt(template, Map.of("reconciler-name", r.getName(), "resource-name", r.getResource()));
                    Files.writeString(new File(new File(helmDir, TEMPLATES_DIR),
                            r.getResource() + "-crd-role-binding.yaml").toPath(), res);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void addClusterRolesForReconcilerPrimaries(File helmDir, List<ReconcilerDescriptor> reconcilerValues) {
        reconcilerValues.forEach(val -> {
            try {
                ClusterRole role = new ClusterRole();
                role.setMetadata(new ObjectMetaBuilder()
                        .withName(val.getResource() + CLUSTER_ROLE_NAME_SUFFIX)
                        .build());
                PolicyRule rule = new PolicyRule();
                rule.setApiGroups(List.of(val.getApiGroup()));
                rule.setResources(List.of(
                        val.getResource(),
                        val.getResource() + "/status",
                        val.getResource() + "/finalizers"));
                rule.setVerbs(CRD_ROLE_VERBS);
                role.setRules(List.of(rule));
                var yaml = io.fabric8.kubernetes.client.utils.Serialization.asYaml(role);
                Files.writeString(Paths.get(helmDir.getPath(), TEMPLATES_DIR, val.getResource() + "-crd-cluster-role.yaml"),
                        yaml);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private void addCRDs(File crdDir, GeneratedCRDInfoBuildItem generatedCRDInfoBuildItem) {
        var crdInfos = generatedCRDInfoBuildItem.getCRDGenerationInfo().getCrds().values().stream()
                .flatMap(m -> m.values().stream())
                .collect(Collectors.toList());

        crdInfos.forEach(crdInfo -> {
            try {
                var generateCrdPath = Path.of(crdInfo.getFilePath());
                // replace needed since tests might generate files multiple times
                Files.copy(generateCrdPath, new File(crdDir, generateCrdPath.getFileName().toString()).toPath(),
                        REPLACE_EXISTING);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private void addValuesYaml(File helmDir,
            String image,
            String tag) {
        try {
            var values = new Values();
            values.setVersion(tag);
            var imageWithoutTage = image.replace(":" + tag, "");
            values.setImage(imageWithoutTage);
            var valuesYaml = Serialization.asYaml(values);
            Files.writeString(Path.of(helmDir.getPath(), VALUES_YAML_FILENAME), valuesYaml);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<ReconcilerDescriptor> createReconcilerValues(ReconcilerInfosBuildItem reconcilerInfosBuildItem) {
        return reconcilerInfosBuildItem.getReconcilers().entrySet().stream().map(e -> {
            ReconcilerDescriptor val = new ReconcilerDescriptor();
            val.setApiGroup(e.getValue().associatedResourceInfo()
                    .classInfo().annotation(Group.class).value().value().toString());
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

    private void copyTemplates(File helmDir) {
        for (String template : TEMPLATE_FILES) {
            try (InputStream file = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream("/helm/static/" + template)) {
                Files.copy(file, new File(new File(helmDir, TEMPLATES_DIR), template).toPath(), REPLACE_EXISTING);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private void createRelatedDirectories(File helmDir) {
        createDirIfNotExists(helmDir);
        createDirIfNotExists(new File(helmDir, TEMPLATES_DIR));
        createDirIfNotExists(new File(helmDir, CRD_DIR));
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
