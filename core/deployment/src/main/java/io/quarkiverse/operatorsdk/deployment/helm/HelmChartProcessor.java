package io.quarkiverse.operatorsdk.deployment.helm;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.quarkiverse.operatorsdk.common.ConfigurationUtils;
import io.quarkiverse.operatorsdk.common.DeserializedKubernetesResourcesBuildItem;
import io.quarkiverse.operatorsdk.common.FileUtils;
import io.quarkiverse.operatorsdk.deployment.AddClusterRolesDecorator;
import io.quarkiverse.operatorsdk.deployment.ControllerConfigurationsBuildItem;
import io.quarkiverse.operatorsdk.deployment.GeneratedCRDInfoBuildItem;
import io.quarkiverse.operatorsdk.deployment.helm.model.Chart;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.annotations.Produce;
import io.quarkus.deployment.builditem.ApplicationInfoBuildItem;
import io.quarkus.deployment.pkg.builditem.ArtifactResultBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.kubernetes.deployment.KubernetesConfig;
import io.quarkus.kubernetes.deployment.ResourceNameUtil;
import io.quarkus.qute.Qute;

@BuildSteps(onlyIf = HelmGenerationEnabled.class)
public class HelmChartProcessor {

    private static final Logger log = Logger.getLogger(HelmChartProcessor.class);

    static final String TEMPLATES_DIR = "templates";
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

    @BuildStep
    HelmTargetDirectoryBuildItem createRelatedDirectories(OutputTargetBuildItem outputTarget) {
        final var helmDir = outputTarget.getOutputDirectory().resolve("helm").toFile();
        log.infov("Generating helm chart to {0}", helmDir);
        FileUtils.ensureDirectoryExists(helmDir);
        FileUtils.ensureDirectoryExists(new File(helmDir, TEMPLATES_DIR));
        FileUtils.ensureDirectoryExists(new File(helmDir, CRD_DIR));
        return new HelmTargetDirectoryBuildItem(helmDir);
    }

    @BuildStep
    @Produce(ArtifactResultBuildItem.class)
    void addPrimaryClusterRoleBindings(HelmTargetDirectoryBuildItem helmTargetDirectoryBuildItem,
            ControllerConfigurationsBuildItem controllerConfigurations) {
        final var controllerConfigs = controllerConfigurations.getControllerConfigs().values();

        final String template;
        try (InputStream file = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(CRD_ROLE_BINDING_TEMPLATE_PATH)) {
            if (file == null) {
                throw new IllegalArgumentException("Template file " + CRD_ROLE_BINDING_TEMPLATE_PATH + " doesn't exist");
            }
            template = new String(file.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        final var templatesDir = helmTargetDirectoryBuildItem.getPathToTemplatesDir();

        controllerConfigs.forEach(cc -> {
            try {
                final var name = cc.getName();
                String res = Qute.fmt(template, Map.of("reconciler-name", name));
                Files.writeString(templatesDir.resolve(name + "-crd-role-binding.yaml"), res);

            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @BuildStep
    @Produce(ArtifactResultBuildItem.class)
    void addClusterRolesForReconcilers(HelmTargetDirectoryBuildItem helmTargetDirectoryBuildItem,
            ControllerConfigurationsBuildItem controllerConfigurations) {
        final var controllerConfigs = controllerConfigurations.getControllerConfigs().values();
        final var templatesDir = helmTargetDirectoryBuildItem.getPathToTemplatesDir();

        controllerConfigs.forEach(cc -> {
            try {
                final var name = cc.getName();
                var clusterRole = AddClusterRolesDecorator.createClusterRole(cc);
                var yaml = FileUtils.asYaml(clusterRole);
                Files.writeString(templatesDir.resolve(name + "-crd-cluster-role.yaml"), yaml);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @BuildStep
    @Produce(ArtifactResultBuildItem.class)
    void addExplicitlyAddedKubernetesResources(DeserializedKubernetesResourcesBuildItem generatedKubernetesResources,
            HelmTargetDirectoryBuildItem helmDirBI,
            ApplicationInfoBuildItem appInfo, KubernetesConfig kubernetesConfig) {
        var resources = generatedKubernetesResources.getResources();
        resources = filterOutStandardResources(resources, ResourceNameUtil.getResourceName(kubernetesConfig, appInfo));
        if (!resources.isEmpty()) {
            final var kubernetesManifest = helmDirBI.getPathToTemplatesDir().resolve("kubernetes.yml");
            // Generate a possibly multi-document YAML
            String yaml = resources.stream().map(FileUtils::asYaml).collect(Collectors.joining());
            try {
                Files.writeString(kubernetesManifest, yaml);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private List<HasMetadata> filterOutStandardResources(List<HasMetadata> resources, String operatorName) {
        return resources.stream().filter(r -> {
            if (r instanceof ClusterRole) {
                return !r.getMetadata().getName().endsWith("-cluster-role");
            }
            if (r instanceof ClusterRoleBinding) {
                return !r.getMetadata().getName().endsWith("-crd-validating-role-binding") &&
                        !r.getMetadata().getName().endsWith("-cluster-role-binding");
            }
            if (r instanceof RoleBinding) {
                return !r.getMetadata().getName().equals(operatorName + "-view") &&
                        !r.getMetadata().getName().endsWith("-role-binding");
            }
            if (r instanceof Service || r instanceof Deployment || r instanceof ServiceAccount) {
                return !r.getMetadata().getName().equals(operatorName);
            }
            return true;
        }).collect(Collectors.toList());
    }

    @BuildStep
    @Produce(ArtifactResultBuildItem.class)
    private void addTemplateFiles(HelmTargetDirectoryBuildItem helmDirBI) {
        copyTemplates(helmDirBI.getPathToTemplatesDir(), TEMPLATE_FILES);
    }

    @BuildStep
    @Produce(ArtifactResultBuildItem.class)
    void addGeneratedDeployment(HelmTargetDirectoryBuildItem helmDirBI,
            DeserializedKubernetesResourcesBuildItem deserializedKubernetesResources,
            ControllerConfigurationsBuildItem controllerConfigurations,
            ApplicationInfoBuildItem appInfo) {
        // add an env var for each reconciler's watch namespace in the operator's deployment
        final var deployment = (Deployment) deserializedKubernetesResources.getResources().stream()
                .filter(Deployment.class::isInstance).findFirst()
                .orElseThrow();
        final var envs = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv();
        controllerConfigurations.getControllerConfigs()
                .forEach((name, unused) -> envs.add(new EnvVar(ConfigurationUtils.getNamespacesPropertyName(name, true),
                        "{watchNamespaces}", null)));

        // a bit hacky solution to get the exact placeholder without brackets
        final var template = FileUtils.asYaml(deployment);
        var res = template.replace("\"{watchNamespaces}\"", "{{ .Values.watchNamespaces }}");
        res = res.replaceAll(appInfo.getVersion(), "{{ .Chart.AppVersion }}");
        try {
            Files.writeString(helmDirBI.getPathToTemplatesDir().resolve("deployment.yaml"), res);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @BuildStep
    @Produce(ArtifactResultBuildItem.class)
    private void addCRDs(HelmTargetDirectoryBuildItem helmDirBI, GeneratedCRDInfoBuildItem generatedCRDInfoBuildItem) {
        var crdInfos = generatedCRDInfoBuildItem.getCRDGenerationInfo().getCrds().values().stream()
                .flatMap(m -> m.values().stream())
                .collect(Collectors.toList());

        final var crdDir = helmDirBI.getPathToHelmDir().resolve(CRD_DIR);
        crdInfos.forEach(crdInfo -> {
            try {
                var generateCrdPath = Path.of(crdInfo.getFilePath());
                // replace needed since tests might generate files multiple times
                Files.copy(generateCrdPath, crdDir.resolve(generateCrdPath.getFileName().toString()), REPLACE_EXISTING);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @BuildStep
    @Produce(ArtifactResultBuildItem.class)
    private void addValuesYaml(HelmTargetDirectoryBuildItem helmTargetDirectoryBuildItem) {
        try {
            var values = new Values();
            var valuesYaml = FileUtils.asYaml(values);
            var valuesFile = helmTargetDirectoryBuildItem.getPathToHelmDir().resolve(VALUES_YAML_FILENAME);
            Files.writeString(valuesFile, valuesYaml);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @BuildStep
    @Produce(ArtifactResultBuildItem.class)
    private void addReadmeAndSchema(HelmTargetDirectoryBuildItem helmDirBI) {
        copyTemplates(helmDirBI.getPathToHelmDir(), ROOT_STATIC_FILES);
    }

    @BuildStep
    @Produce(ArtifactResultBuildItem.class)
    void addChartYaml(HelmTargetDirectoryBuildItem helmTargetDirectoryBuildItem,
            ApplicationInfoBuildItem appInfo) {
        try {
            Chart chart = new Chart();
            chart.setName(appInfo.getName());
            chart.setVersion(appInfo.getVersion());
            chart.setAppVersion(appInfo.getVersion());
            chart.setApiVersion("v2");
            var chartYaml = FileUtils.asYaml(chart);
            final var chartFile = helmTargetDirectoryBuildItem.getPathToHelmDir().resolve(CHART_YAML_FILENAME);
            Files.writeString(chartFile, chartYaml);
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
}
