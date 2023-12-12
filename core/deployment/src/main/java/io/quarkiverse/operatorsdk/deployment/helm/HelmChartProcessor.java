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
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.dekorate.helm.model.Chart;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.quarkiverse.operatorsdk.common.DeserializedKubernetesResourcesBuildItem;
import io.quarkiverse.operatorsdk.common.FileUtils;
import io.quarkiverse.operatorsdk.deployment.AddClusterRolesDecorator;
import io.quarkiverse.operatorsdk.deployment.ControllerConfigurationsBuildItem;
import io.quarkiverse.operatorsdk.deployment.GeneratedCRDInfoBuildItem;
import io.quarkiverse.operatorsdk.runtime.BuildTimeOperatorConfiguration;
import io.quarkiverse.operatorsdk.runtime.QuarkusControllerConfiguration;
import io.quarkus.container.spi.ContainerImageInfoBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Produce;
import io.quarkus.deployment.builditem.ApplicationInfoBuildItem;
import io.quarkus.deployment.pkg.builditem.ArtifactResultBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.kubernetes.spi.*;
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
    private static final String[] ROOT_STATIC_FILES = new String[] {
            "README.md",
            "values.schema.json"
    };
    public static final String CHART_YAML_FILENAME = "Chart.yaml";
    public static final String VALUES_YAML_FILENAME = "values.yaml";
    public static final String CRD_DIR = "crds";
    public static final String CRD_ROLE_BINDING_TEMPLATE_PATH = "/helm/crd-role-binding-template.yaml";

    private static class HelmGenerationEnabled implements BooleanSupplier {
        private BuildTimeOperatorConfiguration config;

        @Override
        public boolean getAsBoolean() {
            return config.helm.enabled;
        }
    }

    @BuildStep(onlyIfNot = HelmGenerationEnabled.class)
    @Produce(ArtifactResultBuildItem.class)
    void outputHelmGenerationDisabled() {
        log.debug("Generating Helm chart is disabled");
    }

    @BuildStep(onlyIf = HelmGenerationEnabled.class)
    @Produce(ArtifactResultBuildItem.class) // to make it produce a build item, so it gets executed
    void handleHelmCharts(DeserializedKubernetesResourcesBuildItem generatedKubernetesResources,
            ControllerConfigurationsBuildItem controllerConfigurations,
            GeneratedCRDInfoBuildItem generatedCRDInfoBuildItem,
            OutputTargetBuildItem outputTarget,
            ApplicationInfoBuildItem appInfo,
            ContainerImageInfoBuildItem containerImageInfoBuildItem) {

        final var helmDir = outputTarget.getOutputDirectory().resolve("helm").toFile();
        log.infov("Generating helm chart to {0}", helmDir);
        var controllerConfigs = controllerConfigurations.getControllerConfigs().values();

        final var resources = generatedKubernetesResources.getResources();
        createRelatedDirectories(helmDir);
        addTemplateFiles(helmDir);
        addClusterRolesForReconcilers(helmDir, controllerConfigs);
        addPrimaryClusterRoleBindings(helmDir, controllerConfigs);
        addGeneratedDeployment(helmDir, resources, controllerConfigurations, appInfo);
        addChartYaml(helmDir, appInfo.getName(), appInfo.getVersion());
        addValuesYaml(helmDir, containerImageInfoBuildItem.getTag());
        addReadmeAndSchema(helmDir);
        addCRDs(new File(helmDir, CRD_DIR), generatedCRDInfoBuildItem);
        addExplicitlyAddedKubernetesResources(helmDir, resources, appInfo);
    }

    private void addExplicitlyAddedKubernetesResources(File helmDir,
            List<HasMetadata> resources, ApplicationInfoBuildItem appInfo) {
        resources = filterOutStandardResources(resources, appInfo);
        if (!resources.isEmpty()) {
            addResourceToHelmDir(helmDir, resources);
        }
    }

    private List<HasMetadata> filterOutStandardResources(List<HasMetadata> resources, ApplicationInfoBuildItem appInfo) {
        return resources.stream().filter(r -> {
            if (r instanceof ClusterRole) {
                return !r.getMetadata().getName().endsWith("-cluster-role");
            }
            if (r instanceof ClusterRoleBinding) {
                return !r.getMetadata().getName().endsWith("-crd-validating-role-binding");
            }
            if (r instanceof RoleBinding) {
                return !r.getMetadata().getName().equals(appInfo.getName() + "-view") &&
                        !r.getMetadata().getName().endsWith("-role-binding");
            }
            if (r instanceof Service || r instanceof Deployment || r instanceof ServiceAccount) {
                return !r.getMetadata().getName().equals(appInfo.getName());
            }
            return true;
        }).collect(Collectors.toList());
    }

    private void addResourceToHelmDir(File helmDir, List<HasMetadata> list) {
        String yaml = FileUtils.asYaml(list);
        try {
            Files.writeString(Path.of(helmDir.getPath(), TEMPLATES_DIR, "kubernetes.yml"), yaml);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void addTemplateFiles(File helmDir) {
        copyTemplates(helmDir.toPath().resolve(TEMPLATES_DIR), TEMPLATE_FILES);
    }

    private void addGeneratedDeployment(File helmDir, List<HasMetadata> resources,
            ControllerConfigurationsBuildItem controllerConfigurations,
            ApplicationInfoBuildItem appInfo) {
        Deployment deployment = (Deployment) resources.stream()
                .filter(Deployment.class::isInstance).findFirst()
                .orElseThrow();
        addActualNamespaceConfigPlaceholderToDeployment(deployment, controllerConfigurations);
        var template = FileUtils.asYaml(deployment);
        // a bit hacky solution to get the exact placeholder without brackets
        String res = template.replace("\"{watchNamespaces}\"", "{{ .Values.watchNamespaces }}");
        res = res.replaceAll(appInfo.getVersion(), "{{ .Values.version }}");
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

    private void addReadmeAndSchema(File helmDir) {
        copyTemplates(helmDir.toPath(), ROOT_STATIC_FILES);
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

    private void copyTemplates(Path path, String[] staticTemplateFiles) {
        for (String template : staticTemplateFiles) {
            try (InputStream is = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(HELM_TEMPLATES_STATIC_DIR + template)) {
                if (is == null) {
                    throw new IllegalArgumentException("Template file " + template + " doesn't exist");
                }
                Files.copy(is, path.resolve(template), REPLACE_EXISTING);
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
