package io.quarkiverse.operatorsdk.deployment.helm;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.dekorate.helm.model.Chart;
import io.dekorate.utils.Serialization;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.utils.KubernetesSerialization;
import io.fabric8.kubernetes.model.annotation.Group;
import io.quarkiverse.operatorsdk.deployment.AddClusterRolesDecorator;
import io.quarkiverse.operatorsdk.deployment.ControllerConfigurationsBuildItem;
import io.quarkiverse.operatorsdk.deployment.GeneratedCRDInfoBuildItem;
import io.quarkiverse.operatorsdk.deployment.ReconcilerInfosBuildItem;
import io.quarkiverse.operatorsdk.runtime.BuildTimeOperatorConfiguration;
import io.quarkiverse.operatorsdk.runtime.QuarkusControllerConfiguration;
import io.quarkus.container.spi.ContainerImageInfoBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.ApplicationInfoBuildItem;
import io.quarkus.deployment.pkg.builditem.ArtifactResultBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.kubernetes.spi.ConfiguratorBuildItem;
import io.quarkus.kubernetes.spi.GeneratedKubernetesResourceBuildItem;
import io.quarkus.qute.Qute;

public class HelmChartProcessor {

    private static final Logger log = Logger.getLogger(HelmChartProcessor.class);

    private static final String TEMPLATES_DIR = "templates";
    private static final String[] TEMPLATE_FILES = new String[] {
            "generic-crd-cluster-role.yaml",
            "generic-crd-cluster-role-binding.yaml",
            "service.yaml",
            "serviceaccount.yaml"
    };
    public static final String CHART_YAML_FILENAME = "Chart.yaml";
    public static final String VALUES_YAML_FILENAME = "values.yaml";
    public static final String CRD_DIR = "crds";

    @BuildStep
    public void handleHelmCharts(
            BuildProducer<ArtifactResultBuildItem> dummy,
            List<GeneratedKubernetesResourceBuildItem> generatedResources,
            ControllerConfigurationsBuildItem controllerConfigurations,
            BuildTimeOperatorConfiguration buildTimeConfiguration,
            GeneratedCRDInfoBuildItem generatedCRDInfoBuildItem,
            OutputTargetBuildItem outputTarget,
            ApplicationInfoBuildItem appInfo,
            ContainerImageInfoBuildItem containerImageInfoBuildItem) {

        if (buildTimeConfiguration.helm.enabled) {
            log.infov("Generating helm chart");
            var helmDir = outputTarget.getOutputDirectory().resolve("helm").toFile();
            var controllerConfigs = controllerConfigurations.getControllerConfigs().values();

            createRelatedDirectories(helmDir);
            copyTemplates(helmDir);
            addClusterRolesForReconcilers(helmDir, controllerConfigs);
            addPrimaryClusterRoleBindings(helmDir, controllerConfigs);
            addGeneratedDeployment(helmDir, generatedResources);
            addChartYaml(helmDir, appInfo.getName(), appInfo.getVersion());
            addValuesYaml(helmDir, containerImageInfoBuildItem.getImage(),
                    containerImageInfoBuildItem.getTag());
            addCRDs(new File(helmDir, CRD_DIR), generatedCRDInfoBuildItem);
        } else {
            log.infov("Generating helm chart is disabled");
        }
    }

    // In this way the deployment is customizable with standard properties
    // handle cases if the generated resource is not a deployment?
    private void addGeneratedDeployment(File helmDir, List<GeneratedKubernetesResourceBuildItem> generatedResources) {
        var kubernetesYaml = generatedResources.stream().filter(r -> "kubernetes.yml".equals(r.getName())).findAny();
        kubernetesYaml.ifPresent(yaml -> {
            try {
                KubernetesSerialization serialization = new KubernetesSerialization();
                // is there a better way than to deserialize this?
                ArrayList<HasMetadata> resources = serialization.unmarshal(new ByteArrayInputStream(yaml.getContent()));
                Deployment deployment = (Deployment) resources.stream().filter(r -> r instanceof Deployment).findFirst()
                        .orElseThrow();
                addActualNamespaceConfigPlaceholderToDeployment(deployment);
                var template = serialization.asYaml(deployment);
                // this is a hacky solution to get the exact placeholder without brackets
                String res = template.replace("\"{watchNamespaces}\"", "{{ .Values.watchNamespaces }}");
                Files.writeString(Paths.get(helmDir.getPath(), TEMPLATES_DIR, "deployment.yaml"),
                        res);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private void addActualNamespaceConfigPlaceholderToDeployment(Deployment deployment) {
        var envs = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv();
        envs.add(new EnvVar("QUARKUS_OPERATOR_SDK_NAMESPACES", "{watchNamespaces}", null));
    }

    private void addPrimaryClusterRoleBindings(File helmDir, Collection<QuarkusControllerConfiguration> reconcilerValues) {
        try (InputStream file = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("/helm/crd-role-binding-template.yaml")) {
            String template = new String(file.readAllBytes(), StandardCharsets.UTF_8);
            reconcilerValues.forEach(r -> {
                try {
                    String res = Qute.fmt(template, Map.of("reconciler-name", r.getName()));
                    Files.writeString(new File(new File(helmDir, TEMPLATES_DIR),
                            r.getName() + "-crd-role-binding.yaml").toPath(), res);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("rawtypes")
    private void addClusterRolesForReconcilers(File helmDir,
            Collection<QuarkusControllerConfiguration> controllerConfigurations) {
        controllerConfigurations.forEach(cc -> {
            try {
                var clusterRole = AddClusterRolesDecorator.createClusterRole(cc);
                var yaml = io.fabric8.kubernetes.client.utils.Serialization.asYaml(clusterRole);
                Files.writeString(Paths.get(helmDir.getPath(), TEMPLATES_DIR, cc.getName() + "-crd-cluster-role.yaml"),
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
