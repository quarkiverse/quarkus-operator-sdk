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
import java.util.Optional;
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
import io.quarkiverse.operatorsdk.deployment.ClusterRoles;
import io.quarkiverse.operatorsdk.deployment.ControllerConfigurationsBuildItem;
import io.quarkiverse.operatorsdk.deployment.GeneratedCRDInfoBuildItem;
import io.quarkiverse.operatorsdk.deployment.RoleBindings;
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

// Note that steps of this processor won't be run in dev mode because ArtifactResultBuildItems are only considered in NORMAL mode
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
    public static final String CRD_ADDITIONAL_ROLE_BINDING_TEMPLATE_PATH = "/helm/additional-crd-role-binding-template.yaml";
    public static final String ADDITIONAL_CRD_ROLE_BINDING_YAML = "additional-crd-role-binding.yaml";

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
        if (!controllerConfigs.isEmpty()) {
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
    }

    @BuildStep
    @Produce(ArtifactResultBuildItem.class)
    void addSecondaryClusterRoleBindings(HelmTargetDirectoryBuildItem helmTargetDirectoryBuildItem,
            ControllerConfigurationsBuildItem controllerConfigurations) {
        final var controllerConfigs = controllerConfigurations.getControllerConfigs().values();
        if (!controllerConfigs.isEmpty()) {
            try (InputStream file = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(CRD_ADDITIONAL_ROLE_BINDING_TEMPLATE_PATH)) {
                if (file == null) {
                    throw new IllegalArgumentException(
                            "Template file " + CRD_ADDITIONAL_ROLE_BINDING_TEMPLATE_PATH + " doesn't exist");
                }

                final String template = new String(file.readAllBytes(), StandardCharsets.UTF_8);
                final var templatesDir = helmTargetDirectoryBuildItem.getPathToTemplatesDir();
                final var stringBuilder = new StringBuilder();
                controllerConfigs.forEach(cc -> cc.getAdditionalRBACRoleRefs().forEach(roleRef -> {
                    final String bindingName = RoleBindings.getSpecificRoleBindingName(cc.getName(), roleRef);
                    stringBuilder.append(Qute.fmt(template, Map.of(
                            "role-binding-name", bindingName,
                            "role-ref-kind", roleRef.getKind(),
                            "role-ref-api-group", roleRef.getApiGroup(),
                            "role-ref-name", roleRef.getName())));
                }));
                Files.writeString(templatesDir.resolve(ADDITIONAL_CRD_ROLE_BINDING_YAML), stringBuilder.toString());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
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
                var clusterRole = ClusterRoles.createClusterRole(cc);
                var yaml = FileUtils.asYaml(clusterRole);
                Files.writeString(templatesDir.resolve(name + "-crd-cluster-role.yaml"), yaml);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @BuildStep
    @Produce(ArtifactResultBuildItem.class)
    void addExplicitlyAddedKubernetesResources(
            @SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<DeserializedKubernetesResourcesBuildItem> maybedGeneratedKubeRes,
            HelmTargetDirectoryBuildItem helmDirBI,
            ApplicationInfoBuildItem appInfo, KubernetesConfig kubernetesConfig) {
        maybedGeneratedKubeRes.ifPresent(generatedKubernetesResources -> {
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
        });
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
        }).toList();
    }

    @BuildStep
    @Produce(ArtifactResultBuildItem.class)
    private void addTemplateFiles(HelmTargetDirectoryBuildItem helmDirBI) {
        copyTemplates(helmDirBI.getPathToTemplatesDir(), TEMPLATE_FILES);
    }

    @BuildStep
    @Produce(ArtifactResultBuildItem.class)
    void addGeneratedDeployment(HelmTargetDirectoryBuildItem helmDirBI,
            @SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<DeserializedKubernetesResourcesBuildItem> maybeDeserializedKubeResources,
            ControllerConfigurationsBuildItem controllerConfigurations,
            ApplicationInfoBuildItem appInfo) {
        if (maybeDeserializedKubeResources.isEmpty()) {
            log.warn("No Kubernetes manifests were found, no Helm chart will be generated");
        } else {
            // add an env var for each reconciler's watch namespace in the operator's deployment
            var deployment = (Deployment) maybeDeserializedKubeResources.get().getResources().stream()
                    .filter(Deployment.class::isInstance).findFirst()
                    .orElseThrow();
            // copy the deployment so that changes are not propagated outside of this method
            final var firstContainer = deployment.edit().editSpec().editTemplate().editSpec().editFirstContainer();

            controllerConfigurations.getControllerConfigs()
                    .forEach((name, unused) -> firstContainer.addNewEnv()
                            .withName(ConfigurationUtils.getNamespacesPropertyName(name, true))
                            .withValue("{watchNamespaces}").endEnv());

            deployment = firstContainer.withImage("{image}").endContainer().endSpec().endTemplate().endSpec().build();

            // a bit hacky solution to get the exact placeholder without brackets
            final var template = FileUtils.asYaml(deployment);
            var res = template.replace("\"{watchNamespaces}\"", "{{ .Values.watchNamespaces }}");
            res = res.replace("\"{image}\"", "{{ .Values.image }}");
            res = res.replaceAll(appInfo.getVersion(), "{{ .Chart.AppVersion }}");
            try {
                Files.writeString(helmDirBI.getPathToTemplatesDir().resolve("deployment.yaml"), res);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @BuildStep
    @Produce(ArtifactResultBuildItem.class)
    private void addCRDs(HelmTargetDirectoryBuildItem helmDirBI, GeneratedCRDInfoBuildItem generatedCRDInfoBuildItem) {
        var crdInfos = generatedCRDInfoBuildItem.getCRDGenerationInfo().getCrds().getCRDNameToInfoMappings().values();

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
    private void addValuesYaml(HelmTargetDirectoryBuildItem helmTargetDirectoryBuildItem,
            @SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<DeserializedKubernetesResourcesBuildItem> maybeDeserializedKubeResources) {
        try {

            var deployment = (Deployment) maybeDeserializedKubeResources.get().getResources().stream()
                    .filter(Deployment.class::isInstance).findFirst()
                    .orElseThrow();

            var firstContainerImage = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage();

            var values = new Values();
            values.setImage(firstContainerImage);
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
