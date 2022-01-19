package io.quarkiverse.operatorsdk.csv.deployment;

import static io.quarkus.kubernetes.deployment.Constants.KUBERNETES;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

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
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.quarkiverse.operatorsdk.common.ClassUtils;
import io.quarkiverse.operatorsdk.common.ConfigurationUtils;
import io.quarkiverse.operatorsdk.csv.runtime.CSVGenerationConfiguration;
import io.quarkiverse.operatorsdk.csv.runtime.CSVMetadata;
import io.quarkiverse.operatorsdk.csv.runtime.CSVMetadataHolder;
import io.quarkiverse.operatorsdk.csv.runtime.SharedCSVMetadata;
import io.quarkiverse.operatorsdk.deployment.GeneratedCRDInfoBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.GeneratedFileSystemResourceBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.kubernetes.spi.GeneratedKubernetesResourceBuildItem;

public class CSVProcessor {
    private static final Logger log = Logger.getLogger(CSVProcessor.class);
    private static final DotName SHARED_CSV_METADATA = DotName.createSimple(SharedCSVMetadata.class.getName());
    private static final DotName CSV_METADATA = DotName.createSimple(CSVMetadata.class.getName());

    private CSVGenerationConfiguration configuration;

    @BuildStep
    CSVMetadataBuildItem gatherCSVMetadata(CombinedIndexBuildItem combinedIndexBuildItem,
            GeneratedCRDInfoBuildItem generatedCRDs) {
        final var csvGroupMetadata = new HashMap<String, CSVMetadataHolder>();

        final var augmentedCRInfos = new HashMap<String, AugmentedResourceInfo>();
        final var index = combinedIndexBuildItem.getIndex();
        ClassUtils.getKnownReconcilers(index, log)
                .forEach(info -> {
                    // figure out which group should be used to generate CSV
                    final var name = ConfigurationUtils.getReconcilerName(info);
                    final var csvMetadata = getCSVMetadata(info, name, index);
                    csvGroupMetadata.put(csvMetadata.name, csvMetadata);
                    final var cri = generatedCRDs.getCRDGenerationInfo().getControllerToCustomResourceMappings().get(name);
                    if (cri == null) {
                        log.infov(
                                "Skipping CSV generation for controller ''{0}'' because there is no CRD information associated with it",
                                name);
                    } else {
                        augmentedCRInfos.put(cri.getResourceFullName(), new AugmentedResourceInfo(cri, csvMetadata.name));
                    }
                });

        return new CSVMetadataBuildItem(csvGroupMetadata, augmentedCRInfos);
    }

    @BuildStep
    void generateCSV(OutputTargetBuildItem outputTarget,
            CSVMetadataBuildItem csvMetadata,
            BuildProducer<GeneratedCSVBuildItem> doneGeneratingCSV,
            List<GeneratedKubernetesResourceBuildItem> generatedKubernetesManifests,
            BuildProducer<GeneratedFileSystemResourceBuildItem> generatedCSVs) {
        if (configuration.generateCSV.orElse(false)) {
            try {
                final var outputDir = outputTarget.getOutputDirectory().resolve(KUBERNETES);
                final var serviceAccountName = new String[1];
                final var clusterRole = new ClusterRole[1];
                final var role = new Role[1];
                final var deployment = new Deployment[1];

                generatedKubernetesManifests.stream()
                        .filter(bi -> bi.getName().equals("kubernetes.yml"))
                        .findAny()
                        .ifPresent(
                                bi -> {
                                    final var resources = Serialization
                                            .unmarshalAsList(new ByteArrayInputStream(bi.getContent()));
                                    resources.getItems().forEach(r -> {
                                        if (r instanceof ServiceAccount) {
                                            serviceAccountName[0] = r.getMetadata().getName();
                                            return;
                                        }

                                        if (r instanceof ClusterRole) {
                                            clusterRole[0] = (ClusterRole) r;
                                            return;
                                        }

                                        if (r instanceof Role) {
                                            role[0] = (Role) r;
                                            return;
                                        }

                                        if (r instanceof Deployment) {
                                            deployment[0] = (Deployment) r;
                                            return;
                                        }
                                    });
                                });
                final var generated = CSVGenerator.prepareGeneration(
                        csvMetadata.getAugmentedCustomResourceInfos(), csvMetadata.getCSVMetadata());
                generated.forEach(namedCSVBuilder -> {
                    final var fileName = namedCSVBuilder.getFileName();
                    try {
                        generatedCSVs.produce(
                                new GeneratedFileSystemResourceBuildItem(
                                        Path.of(KUBERNETES, fileName).toString(),
                                        namedCSVBuilder.getYAMLData(serviceAccountName[0],
                                                clusterRole[0], role[0], deployment[0])));
                        log.infov("Generating CSV for {0} controller -> {1}", namedCSVBuilder.getControllerName(),
                                outputDir.resolve(fileName));
                    } catch (IOException e) {
                        log.errorv("Cannot generate CSV for {0}: {1}", namedCSVBuilder.getControllerName(), e.getMessage());
                    }
                });
                doneGeneratingCSV.produce(new GeneratedCSVBuildItem());
            } catch (Exception e) {
                log.infov(e, "Couldn't generate CSV:");
            }
        }
    }

    private CSVMetadataHolder getCSVMetadata(ClassInfo info, String controllerName, IndexView index) {
        return info.interfaceTypes().stream()
                .filter(t -> t.name().equals(SHARED_CSV_METADATA))
                .findFirst()
                .map(t -> {
                    final var metadataHolderType = t.asParameterizedType().arguments().get(0);
                    // need to get the associated ClassInfo to properly resolve the annotations
                    final var metadataHolder = index.getClassByName(metadataHolderType.name());
                    final var csvMetadata = metadataHolder.classAnnotation(CSV_METADATA);
                    return createMetadataHolder(csvMetadata, new CSVMetadataHolder(controllerName));

                })
                .map(mh -> createMetadataHolder(info.classAnnotation(CSV_METADATA), new CSVMetadataHolder(mh)))
                .orElse(new CSVMetadataHolder(controllerName));
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
                maintainers);
    }
}
