package io.quarkiverse.operatorsdk.helm.deployment;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;

import io.quarkiverse.helm.spi.AdditionalHelmCRDBuildItem;
import io.quarkiverse.helm.spi.AdditionalHelmTemplateBuildItem;
import io.quarkiverse.operatorsdk.common.ConfigurationUtils;
import io.quarkiverse.operatorsdk.deployment.ControllerConfigurationsBuildItem;
import io.quarkiverse.operatorsdk.deployment.GeneratedCRDInfoBuildItem;
import io.quarkiverse.operatorsdk.runtime.BuildTimeOperatorConfiguration;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.kubernetes.spi.KubernetesClusterRoleBindingBuildItem;
import io.quarkus.qute.Qute;

/**
 * This processor copies the generated CRDs into the generated Helm chart.
 * The CRDs are also included in the generated Helm tar file when
 * {@code quarkus.helm.create-tar-file=true} is set.
 * <p>
 * It is required to be in a separate extension because we want to include the Helm
 * processing conditionally, based on the presence of the quarkus-helm extension. If
 * the user includes the quarkus-helm extension, this processor will be executed
 * and the CRDs will be copied into the Helm chart output directories. See the
 * quarkus-extension-maven-plugin configuration in the runtime module of this and
 * the operator-sdk core extensions where this is configured.
 */
public class HelmAugmentationProcessor {

    private static final Logger log = Logger.getLogger(HelmAugmentationProcessor.class);

    public static final String CRD_ROLE_BINDING_TEMPLATE_PATH = "/helm/crd-role-binding-template.yaml";
    public static final String VALIDATING_CLUSTER_ROLE_BINDING_PATH = "/helm/validating-cluster-role-binding-template.yaml";

    @BuildStep
    void generatedKubernetesResourcesForHelm(
            ControllerConfigurationsBuildItem controllerConfigurations,
            BuildTimeOperatorConfiguration operatorConfiguration,
            GeneratedCRDInfoBuildItem generatedCRDInfoBuildItem,
            List<KubernetesClusterRoleBindingBuildItem> clusterRoleBindingBuildItems,
            BuildProducer<AdditionalHelmTemplateBuildItem> additionalHelmTemplateBuildItemBuildProducer,
            BuildProducer<AdditionalHelmCRDBuildItem> additionalHelmCRDBuildItemBuildProducer) {
        final var clusterRoleBindingTemplate = parseTemplateFile(CRD_ROLE_BINDING_TEMPLATE_PATH);
        controllerConfigurations.getControllerConfigs().values().forEach(cc -> {
            final var name = cc.getName().replaceAll("\\s+", "-");
            String res = Qute.fmt(clusterRoleBindingTemplate,
                    Map.of("reconciler-name", name, "reconciler-name-config-name",
                            ConfigurationUtils.getNamespacesPropertyName(name, true)));
            String rbName = name + "-crd-role-binding";
            additionalHelmTemplateBuildItemBuildProducer.produce(new AdditionalHelmTemplateBuildItem(
                    rbName + ".yaml", res.getBytes(), "kubernetes"));
        });

        if (operatorConfiguration.crd().validate()) {
            additionalHelmTemplateBuildItemBuildProducer.produce(new AdditionalHelmTemplateBuildItem(
                    "validating-clusterrolebinding.yaml", parseTemplateFile(VALIDATING_CLUSTER_ROLE_BINDING_PATH).getBytes(),
                    "kubernetes"));
        }

        outputWarningIfNeeded(clusterRoleBindingBuildItems);

        var crdInfos = generatedCRDInfoBuildItem.getCRDGenerationInfo().getCrds().getCRDNameToInfoMappings().values();
        if (crdInfos.isEmpty()) {
            return;
        }

        log.infof("Adding %d CRD(s) to the Helm chart via AdditionalHelmCRDBuildItem", crdInfos.size());
        crdInfos.forEach(crdInfo -> {
            try {
                var crdPath = Path.of(crdInfo.getFilePath());
                byte[] content = Files.readAllBytes(crdPath);
                additionalHelmCRDBuildItemBuildProducer.produce(
                        new AdditionalHelmCRDBuildItem(crdPath.getFileName().toString(), content, "kubernetes"));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private String parseTemplateFile(String filename) {
        try (InputStream file = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(filename)) {
            if (file == null) {
                throw new IllegalArgumentException("Template file " + filename + " doesn't exist");
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
