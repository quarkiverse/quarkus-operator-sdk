package io.quarkiverse.operatorsdk.csv.deployment;

import static io.quarkiverse.operatorsdk.deployment.AddClusterRolesDecorator.ALL_VERBS;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

import io.dekorate.utils.Serialization;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.quarkiverse.operatorsdk.common.ConfigurationUtils;
import io.quarkiverse.operatorsdk.common.ResourceInfo;
import io.quarkiverse.operatorsdk.csv.runtime.CSVGenerationConfiguration;
import io.quarkiverse.operatorsdk.csv.runtime.CSVMetadata;
import io.quarkiverse.operatorsdk.csv.runtime.CSVMetadataHolder;
import io.quarkiverse.operatorsdk.csv.runtime.ShareableCSVMetadata;
import io.quarkiverse.operatorsdk.deployment.ConfigurationServiceBuildItem;
import io.quarkiverse.operatorsdk.deployment.GeneratedCRDInfoBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.GeneratedFileSystemResourceBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.kubernetes.spi.GeneratedKubernetesResourceBuildItem;

public class ManifestsProcessor {
    private static final Logger log = Logger.getLogger(ManifestsProcessor.class);
    private static final DotName SHARED_CSV_METADATA = DotName.createSimple(ShareableCSVMetadata.class.getName());
    private static final DotName CSV_METADATA = DotName.createSimple(CSVMetadata.class.getName());
    private static final String MANIFESTS = "manifests";

    @BuildStep
    CSVMetadataBuildItem gatherCSVMetadata(CombinedIndexBuildItem combinedIndexBuildItem,
            ConfigurationServiceBuildItem configurations) {
        final var csvGroupMetadata = new HashMap<String, CSVMetadataHolder>();

        final var controllerConfigs = configurations.getControllerConfigs();
        final var augmentedCRInfos = new HashMap<String, AugmentedResourceInfo>();
        final var index = combinedIndexBuildItem.getIndex();

        index.getAllKnownImplementors(SHARED_CSV_METADATA)
                .forEach(info -> {
                    // figure out which group should be used to generate CSV
                    final var name = ConfigurationUtils.getReconcilerName(info);
                    final var config = controllerConfigs.get(name);
                    final var csvMetadata = getCSVMetadata(info, name, index);
                    csvGroupMetadata.put(csvMetadata.name, csvMetadata);
                    final var resourceFullName = config.getResourceTypeName();
                    final var resourceInfo = ResourceInfo.createFrom(config.getResourceClass(),
                            resourceFullName, name, config.getSpecClassName(), config.getStatusClassName());
                    augmentedCRInfos.put(resourceFullName, new AugmentedResourceInfo(resourceInfo, csvMetadata.name));
                });

        return new CSVMetadataBuildItem(csvGroupMetadata, augmentedCRInfos);
    }

    @BuildStep
    void generateCSV(CSVGenerationConfiguration configuration,
            OutputTargetBuildItem outputTarget,
            CSVMetadataBuildItem csvMetadata,
            BuildProducer<GeneratedCSVBuildItem> doneGeneratingCSV,
            GeneratedCRDInfoBuildItem generatedCustomResourcesDefinitions,
            List<GeneratedKubernetesResourceBuildItem> generatedKubernetesManifests,
            BuildProducer<GeneratedFileSystemResourceBuildItem> generatedCSVs) {
        if (configuration.generateCSV.orElse(false)) {
            try {
                final var outputDir = outputTarget.getOutputDirectory().resolve(MANIFESTS);
                final var serviceAccounts = new LinkedList<ServiceAccount>();
                final var clusterRoleBindings = new LinkedList<ClusterRoleBinding>();
                final var clusterRoles = new LinkedList<ClusterRole>();
                final var roleBindings = new LinkedList<RoleBinding>();
                final var roles = new LinkedList<Role>();
                final var deployments = new LinkedList<Deployment>();

                generatedKubernetesManifests.stream()
                        .filter(bi -> bi.getName().equals("kubernetes.yml"))
                        .findAny()
                        .ifPresent(
                                bi -> {
                                    final var resources = Serialization
                                            .unmarshalAsList(new ByteArrayInputStream(bi.getContent()));
                                    resources.getItems().forEach(r -> {
                                        if (r instanceof ServiceAccount) {
                                            serviceAccounts.add((ServiceAccount) r);
                                            return;
                                        }

                                        if (r instanceof ClusterRoleBinding) {
                                            clusterRoleBindings.add((ClusterRoleBinding) r);
                                            return;
                                        }

                                        if (r instanceof ClusterRole) {
                                            clusterRoles.add((ClusterRole) r);
                                            return;
                                        }

                                        if (r instanceof RoleBinding) {
                                            roleBindings.add((RoleBinding) r);
                                            return;
                                        }

                                        if (r instanceof Role) {
                                            roles.add((Role) r);
                                            return;
                                        }

                                        if (r instanceof Deployment) {
                                            deployments.add((Deployment) r);
                                            return;
                                        }
                                    });
                                });
                final var generated = ManifestsGenerator.prepareGeneration(
                        csvMetadata.getAugmentedCustomResourceInfos(), csvMetadata.getCSVMetadata());
                generated.forEach(manifestBuilder -> {
                    final var fileName = manifestBuilder.getFileName();
                    try {
                        generatedCSVs.produce(
                                new GeneratedFileSystemResourceBuildItem(
                                        Path.of(MANIFESTS, fileName).toString(),
                                        manifestBuilder.getYAMLData(serviceAccounts, clusterRoleBindings, clusterRoles,
                                                roleBindings, roles, deployments)));
                        log.infov("Generating CSV for {0} controller -> {1}", manifestBuilder.getControllerName(),
                                outputDir.resolve(fileName));
                    } catch (IOException e) {
                        log.errorv("Cannot generate CSV for {0}: {1}", manifestBuilder.getControllerName(), e.getMessage());
                    }
                });
                // copy custom resources to the manifests folder
                generatedCustomResourcesDefinitions.getCRDGenerationInfo().getCrds().values().stream()
                        .flatMap(crds -> crds.values().stream())
                        .forEach(crd -> {
                            try {
                                FileUtils.copyFileToDirectory(new File(crd.getFilePath()), outputDir.toFile());
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
                doneGeneratingCSV.produce(new GeneratedCSVBuildItem());
            } catch (Exception e) {
                log.infov(e, "Couldn't generate CSV:");
            }
        }
    }

    private CSVMetadataHolder getCSVMetadata(ClassInfo info, String controllerName, IndexView index) {
        CSVMetadataHolder csvMetadata = new CSVMetadataHolder(controllerName);
        //        csvMetadata = aggregateMetadataFromSharedCsvMetadata(csvMetadata, info, index);
        csvMetadata = aggregateMetadataFromAnnotation(csvMetadata, info);
        return csvMetadata;
    }

    private CSVMetadataHolder aggregateMetadataFromAnnotation(CSVMetadataHolder holder, ClassInfo info) {
        return Optional.ofNullable(info.classAnnotation(CSV_METADATA))
                .map(annotationInstance -> createMetadataHolder(annotationInstance, holder))
                .orElse(holder);
    }

    private CSVMetadataHolder aggregateMetadataFromSharedCsvMetadata(CSVMetadataHolder holder, ClassInfo info,
            IndexView index) {
        return info.interfaceTypes().stream()
                .filter(t -> t.name().equals(SHARED_CSV_METADATA))
                .findFirst()
                .map(t -> {
                    final var metadataHolderType = t.asParameterizedType().arguments().get(0);
                    // need to get the associated ClassInfo to properly resolve the annotations
                    final var metadataHolder = index.getClassByName(metadataHolderType.name());
                    final var csvMetadata = metadataHolder.classAnnotation(CSV_METADATA);
                    return createMetadataHolder(csvMetadata, holder);
                }).orElse(holder);
    }

    private CSVMetadataHolder createMetadataHolder(AnnotationInstance csvMetadata, CSVMetadataHolder mh) {
        final var providerField = csvMetadata.value("provider");
        String providerName = null;
        String providerURL = null;
        if (providerField != null) {
            final var provider = providerField.asNested();
            providerName = ConfigurationUtils.annotationValueOrDefault(provider, "name",
                    AnnotationValue::asString, () -> mh.providerName);
            providerURL = ConfigurationUtils.annotationValueOrDefault(provider, "url",
                    AnnotationValue::asString, () -> mh.providerURL);
        }

        final var maintainersField = csvMetadata.value("maintainers");
        CSVMetadataHolder.Maintainer[] maintainers = null;
        if (maintainersField != null) {
            final var maintainersAnn = maintainersField.asNestedArray();
            maintainers = new CSVMetadataHolder.Maintainer[maintainersAnn.length];
            for (int i = 0; i < maintainersAnn.length; i++) {
                maintainers[i] = new CSVMetadataHolder.Maintainer(
                        ConfigurationUtils.annotationValueOrDefault(maintainersAnn[i], "name",
                                AnnotationValue::asString, () -> null),
                        ConfigurationUtils.annotationValueOrDefault(maintainersAnn[i], "email",
                                AnnotationValue::asString, () -> null));
            }
        } else {
            maintainers = mh.maintainers;
        }

        final var installModesField = csvMetadata.value("installModes");
        CSVMetadataHolder.InstallMode[] installModes = null;
        if (installModesField != null) {
            final var installModesAnn = installModesField.asNestedArray();
            installModes = new CSVMetadataHolder.InstallMode[installModesAnn.length];
            for (int i = 0; i < installModesAnn.length; i++) {
                installModes[i] = new CSVMetadataHolder.InstallMode(
                        ConfigurationUtils.annotationValueOrDefault(installModesAnn[i], "type",
                                AnnotationValue::asString, () -> null),
                        ConfigurationUtils.annotationValueOrDefault(installModesAnn[i], "supported",
                                AnnotationValue::asBoolean, () -> true));
            }
        } else {
            installModes = mh.installModes;
        }

        final var permissionsField = csvMetadata.value("permissionRules");
        CSVMetadataHolder.PermissionRule[] permissionRules = null;
        if (permissionsField != null) {
            final var permissionsAnn = permissionsField.asNestedArray();
            permissionRules = new CSVMetadataHolder.PermissionRule[permissionsAnn.length];
            for (int i = 0; i < permissionsAnn.length; i++) {
                permissionRules[i] = new CSVMetadataHolder.PermissionRule(
                        ConfigurationUtils.annotationValueOrDefault(permissionsAnn[i], "apiGroups",
                                AnnotationValue::asStringArray, () -> null),
                        ConfigurationUtils.annotationValueOrDefault(permissionsAnn[i], "resources",
                                AnnotationValue::asStringArray, () -> null),
                        ConfigurationUtils.annotationValueOrDefault(permissionsAnn[i], "verbs",
                                AnnotationValue::asStringArray, () -> ALL_VERBS),
                        ConfigurationUtils.annotationValueOrDefault(permissionsAnn[i], "serviceAccountName",
                                AnnotationValue::asString, () -> null));
            }
        } else {
            permissionRules = mh.permissionRules;
        }

        return new CSVMetadataHolder(
                ConfigurationUtils.annotationValueOrDefault(csvMetadata, "name",
                        AnnotationValue::asString, () -> mh.name),
                ConfigurationUtils.annotationValueOrDefault(csvMetadata, "description",
                        AnnotationValue::asString, () -> mh.description),
                ConfigurationUtils.annotationValueOrDefault(csvMetadata, "displayName",
                        AnnotationValue::asString, () -> mh.displayName),
                ConfigurationUtils.annotationValueOrDefault(csvMetadata, "keywords",
                        AnnotationValue::asStringArray, () -> mh.keywords),
                providerName, providerURL,
                ConfigurationUtils.annotationValueOrDefault(csvMetadata, "replaces",
                        AnnotationValue::asString, () -> mh.replaces),
                ConfigurationUtils.annotationValueOrDefault(csvMetadata, "version",
                        AnnotationValue::asString, () -> mh.version),
                ConfigurationUtils.annotationValueOrDefault(csvMetadata, "maturity",
                        AnnotationValue::asString, () -> mh.maturity),
                maintainers,
                installModes,
                permissionRules);
    }
}
