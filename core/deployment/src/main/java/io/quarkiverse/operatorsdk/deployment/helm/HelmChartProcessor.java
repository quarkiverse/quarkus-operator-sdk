package io.quarkiverse.operatorsdk.deployment.helm;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.dekorate.helm.model.Chart;
import io.dekorate.utils.Serialization;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.quarkiverse.operatorsdk.common.FileUtils;
import io.quarkiverse.operatorsdk.common.GeneratedResourcesUtils;
import io.quarkiverse.operatorsdk.deployment.AddClusterRolesDecorator;
import io.quarkiverse.operatorsdk.deployment.ControllerConfigurationsBuildItem;
import io.quarkiverse.operatorsdk.deployment.GeneratedCRDInfoBuildItem;
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
    private static final String HELM_TEMPLATES_STATIC_DIR = "/helm/static/";
    private static final String[] TEMPLATE_FILES = new String[] {
            "generic-crd-cluster-role.yaml",
            "generic-crd-cluster-role-binding.yaml",
            "service.yaml",
            "serviceaccount.yaml"
    };
    public static final String CHART_YAML_FILENAME = "Chart.yaml";
    public static final String VALUES_YAML_FILENAME = "values.yaml";
    public static final String CRD_DIR = "crds";
    public static final String CRD_ROLE_BINDING_TEMPLATE_PATH = "/helm/crd-role-binding-template.yaml";

    @BuildStep
    public void handleHelmCharts(
            // to make it produce a build item, so it gets executed
            @SuppressWarnings("unused") BuildProducer<ArtifactResultBuildItem> dummy,
            List<GeneratedKubernetesResourceBuildItem> generatedResources,
            ControllerConfigurationsBuildItem controllerConfigurations,
            BuildTimeOperatorConfiguration buildTimeConfiguration,
            GeneratedCRDInfoBuildItem generatedCRDInfoBuildItem,
            OutputTargetBuildItem outputTarget,
            ApplicationInfoBuildItem appInfo,
            ContainerImageInfoBuildItem containerImageInfoBuildItem) {

        if (buildTimeConfiguration.helm.enabled) {
            final var helmDir = outputTarget.getOutputDirectory().resolve("helm").toFile();
            log.infov("Generating helm chart to {0}", helmDir);
            var controllerConfigs = controllerConfigurations.getControllerConfigs().values();

            createRelatedDirectories(helmDir);
            copyTemplates(helmDir);
            addClusterRolesForReconcilers(helmDir, controllerConfigs);
            addPrimaryClusterRoleBindings(helmDir, controllerConfigs);
            addGeneratedDeployment(helmDir, generatedResources, controllerConfigurations);
            addChartYaml(helmDir, appInfo.getName(), appInfo.getVersion());
            addValuesYaml(helmDir, containerImageInfoBuildItem.getTag());
            addCRDs(new File(helmDir, CRD_DIR), generatedCRDInfoBuildItem);
        } else {
            log.debug("Generating helm chart is disabled");
        }
    }

    private void addGeneratedDeployment(File helmDir, List<GeneratedKubernetesResourceBuildItem> generatedResources,
            ControllerConfigurationsBuildItem controllerConfigurations) {
        final var resources = GeneratedResourcesUtils.loadFrom(generatedResources);
        Deployment deployment = (Deployment) resources.stream()
                .filter(r -> r instanceof Deployment).findFirst()
                .orElseThrow();
        addActualNamespaceConfigPlaceholderToDeployment(deployment, controllerConfigurations);
        var template = FileUtils.asYaml(deployment);
        // a bit solution to get the exact placeholder without brackets
        String res = template.replace("\"{watchNamespaces}\"", "{{ .Values.watchNamespaces }}");
        try {
            Files.writeString(Path.of(helmDir.getPath(), TEMPLATES_DIR, "deployment.yaml"), res);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void addActualNamespaceConfigPlaceholderToDeployment(Deployment deployment,
            ControllerConfigurationsBuildItem controllerConfigurations) {
        controllerConfigurations.getControllerConfigs().values().forEach(c -> {
            var envs = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv();
            envs.add(new EnvVar("QUARKUS_OPERATOR_SDK_CONTROLLERS_" + c.getName().toUpperCase() + "_NAMESPACES",
                    "{watchNamespaces}", null));
            // use this when the global variable issue is fixed
            // envs.add(new EnvVar("QUARKUS_OPERATOR_SDK_NAMESPACES", "{watchNamespaces}", null));
        });

    }

    @SuppressWarnings("rawtypes")
    private void addPrimaryClusterRoleBindings(File helmDir, Collection<QuarkusControllerConfiguration> controllerConfigs) {
        try (InputStream file = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(CRD_ROLE_BINDING_TEMPLATE_PATH)) {
            if (file == null) {
                throw new IllegalArgumentException("Template file " + CRD_ROLE_BINDING_TEMPLATE_PATH + " doesn't exist");
            }
            String template = new String(file.readAllBytes(), StandardCharsets.UTF_8);
            controllerConfigs.forEach(config -> {
                try {
                    final var name = config.getName();
                    String res = Qute.fmt(template, Map.of("reconciler-name", name));
                    Files.writeString(Path.of(helmDir.getPath(), TEMPLATES_DIR,
                            name + "-crd-role-binding.yaml"), res);
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
                Files.writeString(Path.of(helmDir.getPath(), TEMPLATES_DIR, cc.getName() + "-crd-cluster-role.yaml"),
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
                Files.copy(generateCrdPath, Path.of(crdDir.getPath(), generateCrdPath.getFileName().toString()),
                        REPLACE_EXISTING);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private void addValuesYaml(File helmDir, String tag) {
        try {
            var values = new Values();
            values.setVersion(tag);
            var valuesYaml = FileUtils.asYaml(values);
            Files.writeString(Path.of(helmDir.getPath(), VALUES_YAML_FILENAME), valuesYaml);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void addChartYaml(File helmDir, String name, String version) {
        try {
            Chart chart = new Chart();
            chart.setName(name);
            chart.setVersion(version);
            chart.setApiVersion("v2");
            var chartYaml = FileUtils.asYaml(chart);
            Files.writeString(Path.of(helmDir.getPath(), CHART_YAML_FILENAME), chartYaml);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void copyTemplates(File helmDir) {
        final var destinationDir = helmDir.toPath().resolve(TEMPLATES_DIR);
        for (String template : TEMPLATE_FILES) {
            try (InputStream is = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(HELM_TEMPLATES_STATIC_DIR + template)) {
                if (is == null) {
                    throw new IllegalArgumentException("Template file " + template + " doesn't exist");
                }
                Files.copy(is, destinationDir.resolve(template), REPLACE_EXISTING);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private void createRelatedDirectories(File helmDir) {
        FileUtils.ensureDirectoryExists(helmDir);
        FileUtils.ensureDirectoryExists(new File(helmDir, TEMPLATES_DIR));
        FileUtils.ensureDirectoryExists(new File(helmDir, CRD_DIR));
    }

    @BuildStep
    void disableDefaultHelmListener(BuildProducer<ConfiguratorBuildItem> helmConfiguration) {
        helmConfiguration.produce(new ConfiguratorBuildItem(new DisableDefaultHelmListener()));
    }
}
