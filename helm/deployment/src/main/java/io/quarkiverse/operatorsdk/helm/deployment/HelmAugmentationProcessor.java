package io.quarkiverse.operatorsdk.helm.deployment;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jboss.logging.Logger;

import io.quarkiverse.helm.deployment.HelmChartConfig;
import io.quarkiverse.helm.spi.CustomHelmOutputDirBuildItem;
import io.quarkiverse.helm.spi.HelmChartBuildItem;
import io.quarkiverse.operatorsdk.common.FileUtils;
import io.quarkiverse.operatorsdk.deployment.ControllerConfigurationsBuildItem;
import io.quarkiverse.operatorsdk.deployment.GeneratedCRDInfoBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Produce;
import io.quarkus.deployment.pkg.builditem.ArtifactResultBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.kubernetes.spi.KubernetesClusterRoleBindingBuildItem;
import io.quarkus.qute.Qute;

/**
 * This processor copies the generated CRDs into the Helm chart output directories.
 * <p>
 * It is required to be in a separate extension because we want to include the Helm
 * processing conditionally, based on the presence of the quarkus-helm extension. If
 * the user includes the quarkus-helm extension, this processor will be executed
 * and the CRDs will be copied into the Helm chart output directories. See the
 * quarkus-extension-maven-plugin configuration in the runtime modules of this and
 * the operator-sdk core extensions where this is configured.
 */
public class HelmAugmentationProcessor {

    private static final Logger log = Logger.getLogger(HelmAugmentationProcessor.class);

    public static final String CRD_DIR = "crds";
    public static final String TEMPLATES_DIR = "templates";
    public static final String CRD_ROLE_BINDING_TEMPLATE_PATH = "/helm/crd-role-binding-template.yaml";

    @BuildStep
    @Produce(ArtifactResultBuildItem.class)
    void addCRDsToHelmChart(
            List<HelmChartBuildItem> helmChartBuildItems,
            HelmChartConfig helmChartConfig,
            @SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<CustomHelmOutputDirBuildItem> customHelmOutputDirBuildItem,
            OutputTargetBuildItem outputTargetBuildItem,
            GeneratedCRDInfoBuildItem generatedCRDInfoBuildItem,
            List<KubernetesClusterRoleBindingBuildItem> clusterRoleBindingBuildItems,
            ControllerConfigurationsBuildItem controllerConfigurations) {
        log.infof("Adding CRDs to the following Helm charts - %s", helmChartBuildItems);
        outputWarningIfNeeded(clusterRoleBindingBuildItems);

        final var helmOutputDirectory = customHelmOutputDirBuildItem
                .map(CustomHelmOutputDirBuildItem::getOutputDir)
                .orElse(outputTargetBuildItem.getOutputDirectory().resolve(helmChartConfig.outputDirectory()));

        var crdInfos = generatedCRDInfoBuildItem.getCRDGenerationInfo().getCrds().getCRDNameToInfoMappings().values();

        helmChartBuildItems.forEach(helmChartBuildItem -> {
            Path chartDir = helmOutputDirectory
                    .resolve(helmChartBuildItem.getDeploymentTarget())
                    .resolve(helmChartBuildItem.getName());

            // CRDs
            final var crdDir = chartDir
                    .resolve(CRD_DIR);

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

            // RoleBindings allowing runtime Helm overrides
            final var template = parseTemplateRoleBindingFile();
            final var templatesDir = chartDir.resolve(TEMPLATES_DIR);

            controllerConfigurations.getControllerConfigs().values().forEach(cc -> {
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

    private String parseTemplateRoleBindingFile() {
        try (InputStream file = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(CRD_ROLE_BINDING_TEMPLATE_PATH)) {
            if (file == null) {
                throw new IllegalArgumentException("Template file " + CRD_ROLE_BINDING_TEMPLATE_PATH + " doesn't exist");
            }
            return new String(file.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void outputWarningIfNeeded(List<KubernetesClusterRoleBindingBuildItem> clusterRoleBindingBuildItems) {
        clusterRoleBindingBuildItems
                .forEach(clusterRoleBindingBuildItem -> Arrays.stream(clusterRoleBindingBuildItem.getSubjects())
                        .forEach(subject -> {
                            if (subject.getNamespace() == null) {
                                log.warnf(
                                        "ClusterRoleBinding '%s' REQUIRES a namespace for the operator ServiceAccount, which has NOT been provided. "
                                                +
                                                "You can specify the ServiceAccount's namespace using the " +
                                                "'quarkus.kubernetes.rbac.service-accounts.<service account name>.namespace=<service account namespace>'"
                                                +
                                                "property (or, alternatively, 'quarkus.kubernetes.namespace', though using this property will use the specified "
                                                +
                                                "namespace for ALL your resources. The Helm chart generated by this extension will most likely fail to install if "
                                                +
                                                "the namespace is not provided.",
                                        clusterRoleBindingBuildItem.getName());
                            }
                        }));
    }
}
