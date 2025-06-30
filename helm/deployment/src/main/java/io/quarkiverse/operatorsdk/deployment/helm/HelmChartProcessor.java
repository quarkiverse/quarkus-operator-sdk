package io.quarkiverse.operatorsdk.deployment.helm;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

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

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.quarkiverse.helm.deployment.HelmChartConfig;
import io.quarkiverse.helm.deployment.HelmEnabled;
import io.quarkiverse.helm.spi.CustomHelmOutputDirBuildItem;
import io.quarkiverse.helm.spi.HelmChartBuildItem;
import io.quarkiverse.operatorsdk.common.ConfigurationUtils;
import io.quarkiverse.operatorsdk.common.DeserializedKubernetesResourcesBuildItem;
import io.quarkiverse.operatorsdk.common.FileUtils;
import io.quarkiverse.operatorsdk.deployment.ControllerConfigurationsBuildItem;
import io.quarkiverse.operatorsdk.deployment.GeneratedCRDInfoBuildItem;
import io.quarkiverse.operatorsdk.deployment.RoleBindings;
import io.quarkus.deployment.annotations.BuildProducer;
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
@BuildSteps(onlyIf = HelmEnabled.class)
public class HelmChartProcessor {

    private static final Logger log = Logger.getLogger(HelmChartProcessor.class);

    static final String TEMPLATES_DIR = "templates";
    public static final String CRD_DIR = "crds";
    public static final String CRD_ROLE_BINDING_TEMPLATE_PATH = "/helm/crd-role-binding-template.yaml";
    public static final String CRD_ADDITIONAL_ROLE_BINDING_TEMPLATE_PATH = "/helm/additional-crd-role-binding-template.yaml";
    public static final String ADDITIONAL_CRD_ROLE_BINDING_YAML = "additional-crd-role-binding.yaml";

    @BuildStep
    void produceHelmTargetDirectories(
            BuildProducer<HelmTargetDirectoryBuildItem> helmTargetDirectoryBuildItemBuildProducer,
            List<HelmChartBuildItem> helmChartBuildItems,
            HelmChartConfig helmChartConfig,
            @SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<CustomHelmOutputDirBuildItem> customHelmOutputDirBuildItem,
            OutputTargetBuildItem outputTargetBuildItem) {
        log.info("Generating QOSDK Helm resources for the following Helm charts - " + helmChartBuildItems);
        final var helmOutputDirectory = customHelmOutputDirBuildItem
                .map(CustomHelmOutputDirBuildItem::getOutputDir)
                .orElse(outputTargetBuildItem.getOutputDirectory().resolve(helmChartConfig.outputDirectory()));
        helmChartBuildItems.forEach(helmChartBuildItem -> {
            final var helmTargetDirectory = helmOutputDirectory
                    .resolve(helmChartBuildItem.getDeploymentTarget())
                    .resolve(helmChartBuildItem.getName());
            helmTargetDirectoryBuildItemBuildProducer.produce(new HelmTargetDirectoryBuildItem(helmTargetDirectory.toFile()));
        });
    }

    @BuildStep
    @Produce(ArtifactResultBuildItem.class)
    void addPrimaryClusterRoleBindings(List<HelmTargetDirectoryBuildItem> helmTargetDirectories,
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

            helmTargetDirectories.forEach(helmTargetDir -> {
                var templatesDir = helmTargetDir.getPathToTemplatesDir();

                controllerConfigs.forEach(cc -> {
                    try {
                        final var name = cc.getName();
                        String res = Qute.fmt(template, Map.of("reconciler-name", name));
                        Files.writeString(templatesDir.resolve(name + "-crd-role-binding.yaml"), res);

                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                });
            });
        }
    }

    @BuildStep
    @Produce(ArtifactResultBuildItem.class)
    void addSecondaryClusterRoleBindings(List<HelmTargetDirectoryBuildItem> helmTargetDirectories,
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
                helmTargetDirectories.forEach(helmTargetDirectory -> {
                    final var templatesDir = helmTargetDirectory.getPathToTemplatesDir();
                    final var stringBuilder = new StringBuilder();
                    controllerConfigs.forEach(cc -> cc.getAdditionalRBACRoleRefs().forEach(roleRef -> {
                        final String bindingName = RoleBindings.getSpecificRoleBindingName(cc.getName(), roleRef);
                        stringBuilder.append(Qute.fmt(template, Map.of(
                                "role-binding-name", bindingName,
                                "role-ref-kind", roleRef.getKind(),
                                "role-ref-api-group", roleRef.getApiGroup(),
                                "role-ref-name", roleRef.getName())));
                    }));
                    if (!stringBuilder.isEmpty()) {
                        try {
                            Files.writeString(templatesDir.resolve(ADDITIONAL_CRD_ROLE_BINDING_YAML), stringBuilder.toString());
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    }
                });
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @BuildStep
    @Produce(ArtifactResultBuildItem.class)
    void addExplicitlyAddedKubernetesResources(
            @SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<DeserializedKubernetesResourcesBuildItem> maybedGeneratedKubeRes,
            List<HelmTargetDirectoryBuildItem> helmTargetDirectories,
            ApplicationInfoBuildItem appInfo, KubernetesConfig kubernetesConfig) {
        maybedGeneratedKubeRes.ifPresent(generatedKubernetesResources -> {
            var resources = generatedKubernetesResources.getResources();
            resources = filterOutStandardResources(resources, ResourceNameUtil.getResourceName(kubernetesConfig, appInfo));
            if (!resources.isEmpty()) {
                for (HelmTargetDirectoryBuildItem helmTargetDirectory : helmTargetDirectories) {
                    final var kubernetesManifest = helmTargetDirectory.getPathToTemplatesDir().resolve("kubernetes.yml");
                    // Generate a possibly multi-document YAML
                    String yaml = resources.stream().map(FileUtils::asYaml).collect(Collectors.joining());
                    try {
                        Files.writeString(kubernetesManifest, yaml);
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
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
    void addGeneratedDeployment(List<HelmTargetDirectoryBuildItem> helmTargetDirectories,
            @SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<DeserializedKubernetesResourcesBuildItem> maybeDeserializedKubeResources,
            ControllerConfigurationsBuildItem controllerConfigurations,
            ApplicationInfoBuildItem appInfo) throws IOException {
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
            var res = template.replace("\"{watchNamespaces}\"", "{{ .Values.app.watchNamespaces }}");
            res = res.replace("\"{image}\"", "{{ .Values.image }}");
            res = res.replaceAll(appInfo.getVersion(), "{{ .Chart.AppVersion }}");
            for (HelmTargetDirectoryBuildItem helmTargetDir : helmTargetDirectories) {
                Files.writeString(helmTargetDir.getPathToTemplatesDir().resolve("deployment.yaml"), res);
            }
        }
    }

    @BuildStep
    @Produce(ArtifactResultBuildItem.class)
    private void addCRDs(List<HelmTargetDirectoryBuildItem> helmTargetDirectories,
            GeneratedCRDInfoBuildItem generatedCRDInfoBuildItem) {
        var crdInfos = generatedCRDInfoBuildItem.getCRDGenerationInfo().getCrds().getCRDNameToInfoMappings().values();

        helmTargetDirectories.forEach(helmTargetDirectory -> {
            final var crdDir = helmTargetDirectory.getPathToHelmDir().resolve(CRD_DIR);
            FileUtils.ensureDirectoryExists(crdDir.toFile());
            crdInfos.forEach(crdInfo -> {
                try {
                    var generateCrdPath = Path.of(crdInfo.getFilePath());
                    // replace needed since tests might generate files multiple times
                    Files.copy(generateCrdPath, crdDir.resolve(generateCrdPath.getFileName().toString()), REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
        });
    }

}
