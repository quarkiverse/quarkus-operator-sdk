package io.quarkiverse.operatorsdk.bundle.deployment;

import static io.quarkiverse.operatorsdk.annotations.RBACVerbs.ALL_COMMON_VERBS;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.quarkiverse.operatorsdk.annotations.CSVMetadata;
import io.quarkiverse.operatorsdk.annotations.SharedCSVMetadata;
import io.quarkiverse.operatorsdk.bundle.runtime.BundleGenerationConfiguration;
import io.quarkiverse.operatorsdk.bundle.runtime.CSVMetadataHolder;
import io.quarkiverse.operatorsdk.common.*;
import io.quarkiverse.operatorsdk.deployment.GeneratedCRDInfoBuildItem;
import io.quarkiverse.operatorsdk.deployment.VersionBuildItem;
import io.quarkiverse.operatorsdk.runtime.BuildTimeOperatorConfiguration;
import io.quarkiverse.operatorsdk.runtime.CRDInfo;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.ApplicationInfoBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.GeneratedFileSystemResourceBuildItem;
import io.quarkus.deployment.pkg.builditem.JarBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.kubernetes.deployment.KubernetesCommonHelper;
import io.quarkus.kubernetes.deployment.KubernetesConfig;
import io.quarkus.kubernetes.deployment.ResourceNameUtil;

public class BundleProcessor {

    private static final Logger log = Logger.getLogger(BundleProcessor.class);
    private static final DotName SHARED_CSV_METADATA = DotName.createSimple(SharedCSVMetadata.class.getName());
    private static final DotName CSV_METADATA = DotName.createSimple(CSVMetadata.class.getName());
    private static final String BUNDLE = "bundle";
    private static final String DEFAULT_PROVIDER_NAME = System.getProperty("user.name");
    public static final String CRD_DISPLAY_NAME = "CRD_DISPLAY_NAME";
    public static final String CRD_DESCRIPTION = "CRD_DESCRIPTION";

    private static class IsGenerationEnabled implements BooleanSupplier {

        private BundleGenerationConfiguration config;

        @Override
        public boolean getAsBoolean() {
            return config.enabled;
        }
    }

    @SuppressWarnings({ "unused" })
    @BuildStep(onlyIf = IsGenerationEnabled.class)
    CSVMetadataBuildItem gatherCSVMetadata(KubernetesConfig kubernetesConfig,
            ApplicationInfoBuildItem appConfiguration,
            BundleGenerationConfiguration bundleConfiguration,
            CombinedIndexBuildItem combinedIndexBuildItem,
            JarBuildItem jarBuildItem) {
        final var index = combinedIndexBuildItem.getIndex();
        final var defaultName = bundleConfiguration.packageName
                .orElse(ResourceNameUtil.getResourceName(kubernetesConfig, appConfiguration));

        // note that version, replaces, etc. should probably be settable at the reconciler level
        // use version specified in bundle configuration, if not use the one extracted from the project, if available
        final var version = kubernetesConfig.getVersion().orElse(appConfiguration.getVersion());
        final var defaultVersion = bundleConfiguration.version
                .orElse(ApplicationInfoBuildItem.UNSET_VALUE.equals(version) ? null : version);

        final var defaultReplaces = bundleConfiguration.replaces.orElse(null);

        final var defaultProviderURL = getDefaultProviderURLFromSCMInfo(appConfiguration, jarBuildItem);
        final var sharedMetadataHolders = getSharedMetadataHolders(defaultName, defaultVersion, defaultReplaces, index,
                defaultProviderURL);
        final var csvGroups = new HashMap<CSVMetadataHolder, List<ReconcilerAugmentedClassInfo>>();

        ClassUtils.getKnownReconcilers(index, log)
                .forEach(reconcilerInfo -> {
                    // figure out which group should be used to generate CSV
                    final var name = reconcilerInfo.name();
                    log.debugv("Processing reconciler: {0}", name);

                    // Check whether the reconciler must be shipped using a custom bundle
                    final var csvMetadataAnnotation = reconcilerInfo.classInfo()
                            .declaredAnnotation(CSV_METADATA);
                    final var sharedMetadataName = getBundleName(csvMetadataAnnotation, defaultName);
                    final var isNameInferred = defaultName.equals(sharedMetadataName);

                    var csvMetadata = sharedMetadataHolders.get(sharedMetadataName);
                    if (csvMetadata == null) {
                        final var origin = reconcilerInfo.classInfo().name().toString();

                        if (!sharedMetadataName.equals(defaultName)) {
                            final var maybeExistingOrigin = csvGroups.keySet().stream()
                                    .filter(mh -> mh.bundleName.equals(sharedMetadataName))
                                    .map(CSVMetadataHolder::getOrigin)
                                    .findFirst();
                            if (maybeExistingOrigin.isPresent()) {
                                throw new IllegalStateException("Reconcilers '" + maybeExistingOrigin.get()
                                        + "' and '" + origin
                                        + "' are using the same bundle name '" + sharedMetadataName
                                        + "' but no SharedCSVMetadata implementation with that name exists. Please create a SharedCSVMetadata with that name to have one single source of truth and reference it via CSVMetadata annotations using that name on your reconcilers.");
                            }
                        }
                        csvMetadata = createMetadataHolder(csvMetadataAnnotation,
                                new CSVMetadataHolder(sharedMetadataName, defaultVersion, defaultReplaces,
                                        DEFAULT_PROVIDER_NAME, defaultProviderURL, origin));
                        if (DEFAULT_PROVIDER_NAME.equals(csvMetadata.providerName)) {
                            log.warnv(
                                    "It is recommended that you provide a provider name provided for {0}: ''{1}'' was used as default value.",
                                    origin, DEFAULT_PROVIDER_NAME);
                        }
                    }
                    log.infov("Assigning ''{0}'' reconciler to {1}",
                            reconcilerInfo.nameOrFailIfUnset(),
                            getMetadataOriginInformation(csvMetadataAnnotation, isNameInferred, csvMetadata));

                    csvGroups.computeIfAbsent(csvMetadata, m -> new ArrayList<>()).add(
                            augmentReconcilerInfo(reconcilerInfo));
                });

        return new CSVMetadataBuildItem(csvGroups);
    }

    private static String getDefaultProviderURLFromSCMInfo(ApplicationInfoBuildItem appConfiguration,
            JarBuildItem jarBuildItem) {
        final var maybeProject = KubernetesCommonHelper.createProject(appConfiguration, Optional.empty(),
                jarBuildItem.getPath());
        return maybeProject.map(project -> {
            final var scmInfo = project.getScmInfo();
            if (scmInfo != null) {
                var origin = scmInfo.getRemote().get("origin");
                if (origin != null) {
                    try {
                        int atSign = origin.indexOf('@');
                        if (atSign > 0) {
                            origin = origin.substring(atSign + 1);
                            origin = origin.replaceFirst(":", "/");
                            origin = "https://" + origin;
                        }

                        int dotGit = origin.indexOf(".git");
                        if (dotGit > 0 && dotGit < origin.length() - 1) {
                            origin = origin.substring(0, dotGit);
                        }
                        return origin;
                    } catch (Exception e) {
                        log.warnv("Couldn't parse SCM information: {0}", origin);
                    }
                }
            }
            return null;
        }).orElse(null);
    }

    private static ReconcilerAugmentedClassInfo augmentReconcilerInfo(
            ReconcilerAugmentedClassInfo reconcilerInfo) {
        // if primary resource is a CR, check if it is annotated with CSVMetadata and augment it if it is
        final ReconciledAugmentedClassInfo<?> primaryCI = reconcilerInfo.associatedResourceInfo();
        augmentResourceInfoIfCR(primaryCI);

        reconcilerInfo.getDependentResourceInfos().forEach(draci -> {
            // if the dependent is a CR, check if it is annotated with CSVMetadata and augment it if it is
            final ReconciledAugmentedClassInfo<?> reconciledAugmentedClassInfo = draci.associatedResourceInfo();
            augmentResourceInfoIfCR(reconciledAugmentedClassInfo);
        });
        return reconcilerInfo;
    }

    private static void augmentResourceInfoIfCR(ReconciledAugmentedClassInfo<?> reconciledAugmentedClassInfo) {
        if (reconciledAugmentedClassInfo.isCR()) {
            final var csvMetadata = reconciledAugmentedClassInfo.classInfo().annotation(CSV_METADATA);
            if (csvMetadata != null) {
                // extract display name and description
                final var displayName = ConfigurationUtils.annotationValueOrDefault(csvMetadata,
                        "displayName", AnnotationValue::asString,
                        () -> reconciledAugmentedClassInfo.asResourceTargeting().kind());
                reconciledAugmentedClassInfo.setExtendedInfo(CRD_DISPLAY_NAME, displayName);
                final var description = ConfigurationUtils.annotationValueOrDefault(
                        csvMetadata,
                        "description", AnnotationValue::asString,
                        () -> null);
                if (description != null) {
                    reconciledAugmentedClassInfo.setExtendedInfo(CRD_DESCRIPTION, description);
                }
            }
        }
    }

    private String getMetadataOriginInformation(AnnotationInstance csvMetadataAnnotation, boolean isNameInferred,
            CSVMetadataHolder metadataHolder) {
        final var isDefault = csvMetadataAnnotation == null;
        final var actualName = metadataHolder.bundleName;
        if (isDefault) {
            return "default bundle automatically named '" + actualName + "'";
        } else {
            return "bundle " + (isNameInferred ? "automatically " : "") + "named '"
                    + actualName + "' defined by '" + metadataHolder.getOrigin() + "'";
        }
    }

    @SuppressWarnings("unused")
    @BuildStep(onlyIf = IsGenerationEnabled.class)
    void generateBundle(ApplicationInfoBuildItem configuration,
            KubernetesConfig kubernetesConfig,
            BundleGenerationConfiguration bundleConfiguration,
            BuildTimeOperatorConfiguration operatorConfiguration,
            OutputTargetBuildItem outputTarget,
            CSVMetadataBuildItem csvMetadata,
            VersionBuildItem versionBuildItem,
            BuildProducer<GeneratedBundleBuildItem> doneGeneratingCSV,
            GeneratedCRDInfoBuildItem generatedCustomResourcesDefinitions,
            DeserializedKubernetesResourcesBuildItem generatedKubernetesResources,
            BuildProducer<GeneratedFileSystemResourceBuildItem> generatedCSVs) {
        final var crds = generatedCustomResourcesDefinitions.getCRDGenerationInfo().getCrds()
                .values().stream()
                .flatMap(entry -> entry.values().stream())
                .collect(Collectors.toMap(CRDInfo::getCrdName, Function.identity()));
        final var outputDir = outputTarget.getOutputDirectory().resolve(BUNDLE);
        final var serviceAccounts = new LinkedList<ServiceAccount>();
        final var clusterRoleBindings = new LinkedList<ClusterRoleBinding>();
        final var clusterRoles = new LinkedList<ClusterRole>();
        final var roleBindings = new LinkedList<RoleBinding>();
        final var roles = new LinkedList<Role>();
        final var deployments = new LinkedList<Deployment>();

        final var resources = generatedKubernetesResources.getResources();
        resources.forEach(r -> {
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
            }
        });

        final var deploymentName = ResourceNameUtil.getResourceName(kubernetesConfig, configuration);
        final var generated = BundleGenerator.prepareGeneration(bundleConfiguration, operatorConfiguration,
                versionBuildItem.getVersion(),
                csvMetadata.getCsvGroups(), crds, outputTarget.getOutputDirectory(), deploymentName);
        generated.forEach(manifestBuilder -> {
            final var fileName = manifestBuilder.getFileName();
            try {
                generatedCSVs.produce(
                        new GeneratedFileSystemResourceBuildItem(
                                Path.of(BUNDLE).resolve(manifestBuilder.getName()).resolve(fileName).toString(),
                                manifestBuilder.getManifestData(serviceAccounts, clusterRoleBindings, clusterRoles,
                                        roleBindings, roles, deployments)));
                log.infov("Generating {0} for ''{1}'' controller -> {2}",
                        manifestBuilder.getManifestType(),
                        manifestBuilder.getName(),
                        outputDir.resolve(manifestBuilder.getName()).resolve(fileName));
            } catch (IOException e) {
                log.errorv("Cannot generate {0} for ''{1}'' controller: {2}",
                        manifestBuilder.getManifestType(), manifestBuilder.getName(), e.getMessage());
            }
        });
        doneGeneratingCSV.produce(new GeneratedBundleBuildItem());
    }

    private Map<String, CSVMetadataHolder> getSharedMetadataHolders(String name, String version, String defaultReplaces,
            IndexView index, String vcsUrl) {
        CSVMetadataHolder csvMetadata = new CSVMetadataHolder(name, version, defaultReplaces, vcsUrl, DEFAULT_PROVIDER_NAME,
                "default");
        final var sharedMetadataImpls = index.getAllKnownImplementors(SHARED_CSV_METADATA);
        final var result = new HashMap<String, CSVMetadataHolder>(sharedMetadataImpls.size() + 1);
        sharedMetadataImpls.forEach(sharedMetadataImpl -> {
            final var csvMetadataAnn = sharedMetadataImpl.declaredAnnotation(CSV_METADATA);
            if (csvMetadataAnn != null) {
                final var origin = sharedMetadataImpl.name().toString();
                final var metadataHolder = createMetadataHolder(csvMetadataAnn, csvMetadata, origin);
                final var existing = result.get(metadataHolder.bundleName);
                if (existing != null) {
                    throw new IllegalStateException(
                            "Only one SharedCSVMetadata named " + metadataHolder.bundleName
                                    + " can be defined. Was defined on (at least): " + existing.getOrigin() + " and " + origin);
                }
                result.put(metadataHolder.bundleName, metadataHolder);
            }
        });
        return result;
    }

    private static String getBundleName(AnnotationInstance csvMetadata, String defaultName) {
        if (csvMetadata == null) {
            return defaultName;
        } else {
            final var bundleName = csvMetadata.value("bundleName");
            if (bundleName != null) {
                return bundleName.asString();
            } else {
                return Optional.ofNullable(csvMetadata.value("name"))
                        .map(AnnotationValue::asString)
                        .orElse(defaultName);
            }
        }
    }

    private CSVMetadataHolder createMetadataHolder(AnnotationInstance csvMetadata,
            CSVMetadataHolder mh) {
        return createMetadataHolder(csvMetadata, mh, mh.getOrigin());
    }

    private CSVMetadataHolder createMetadataHolder(AnnotationInstance csvMetadata, CSVMetadataHolder mh,
            String origin) {
        if (csvMetadata == null) {
            return mh;
        }

        final var providerField = csvMetadata.value("provider");
        String providerName = mh.providerName;
        String providerURL = mh.providerURL;
        if (providerField != null) {
            final var provider = providerField.asNested();
            providerName = ConfigurationUtils.annotationValueOrDefault(provider, "name",
                    AnnotationValue::asString, () -> mh.providerName);
            providerURL = ConfigurationUtils.annotationValueOrDefault(provider, "url",
                    AnnotationValue::asString, () -> mh.providerURL);
        }

        final var annotationsField = csvMetadata.value("annotations");
        CSVMetadataHolder.Annotations annotations;
        if (annotationsField != null) {
            final var annotationsAnn = annotationsField.asNested();

            final var othersAnn = annotationsAnn.value("others");
            Map<String, String> others = Collections.emptyMap();
            if (othersAnn != null) {
                final var othersArray = othersAnn.asNestedArray();
                others = new HashMap<>(othersArray.length);
                for (AnnotationInstance other : othersArray) {
                    others.put(other.value("name").asString(), other.value("value").asString());
                }
            }
            annotations = new CSVMetadataHolder.Annotations(
                    ConfigurationUtils.annotationValueOrDefault(annotationsAnn, "containerImage",
                            AnnotationValue::asString, () -> null),
                    ConfigurationUtils.annotationValueOrDefault(annotationsAnn, "repository",
                            AnnotationValue::asString, () -> null),
                    ConfigurationUtils.annotationValueOrDefault(annotationsAnn, "capabilities",
                            AnnotationValue::asString, () -> null),
                    ConfigurationUtils.annotationValueOrDefault(annotationsAnn, "categories",
                            AnnotationValue::asString, () -> null),
                    ConfigurationUtils.annotationValueOrDefault(annotationsAnn, "certified",
                            AnnotationValue::asBoolean, () -> false),
                    ConfigurationUtils.annotationValueOrDefault(annotationsAnn, "almExamples",
                            AnnotationValue::asString, () -> null),
                    ConfigurationUtils.annotationValueOrDefault(annotationsAnn, "skipRange",
                            AnnotationValue::asString, () -> null),
                    others);
        } else {
            annotations = mh.annotations;
        }

        final var maintainersField = csvMetadata.value("maintainers");
        CSVMetadataHolder.Maintainer[] maintainers;
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

        final var linksField = csvMetadata.value("links");
        CSVMetadataHolder.Link[] links;
        if (linksField != null) {
            final var linkAnn = linksField.asNestedArray();
            links = new CSVMetadataHolder.Link[linkAnn.length];
            for (int i = 0; i < linkAnn.length; i++) {
                links[i] = new CSVMetadataHolder.Link(
                        ConfigurationUtils.annotationValueOrDefault(linkAnn[i], "name",
                                AnnotationValue::asString, () -> null),
                        ConfigurationUtils.annotationValueOrDefault(linkAnn[i], "url",
                                AnnotationValue::asString, () -> null));
            }
        } else {
            links = mh.links;
        }

        final var iconField = csvMetadata.value("icon");
        CSVMetadataHolder.Icon[] icon;
        if (iconField != null) {
            final var iconAnn = iconField.asNestedArray();
            icon = new CSVMetadataHolder.Icon[iconAnn.length];
            for (int i = 0; i < iconAnn.length; i++) {
                icon[i] = new CSVMetadataHolder.Icon(
                        ConfigurationUtils.annotationValueOrDefault(iconAnn[i], "fileName",
                                AnnotationValue::asString, () -> null),
                        ConfigurationUtils.annotationValueOrDefault(iconAnn[i], "mediatype",
                                AnnotationValue::asString, () -> CSVMetadata.Icon.DEFAULT_MEDIA_TYPE));
            }
        } else {
            icon = mh.icon;
        }

        final var installModesField = csvMetadata.value("installModes");
        CSVMetadataHolder.InstallMode[] installModes;
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
        CSVMetadataHolder.PermissionRule[] permissionRules;
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
                                AnnotationValue::asStringArray, () -> ALL_COMMON_VERBS),
                        ConfigurationUtils.annotationValueOrDefault(permissionsAnn[i], "serviceAccountName",
                                AnnotationValue::asString, () -> null));
            }
        } else {
            permissionRules = mh.permissionRules;
        }

        final var requiredCRDsField = csvMetadata.value("requiredCRDs");
        CSVMetadataHolder.RequiredCRD[] requiredCRDs;
        if (requiredCRDsField != null) {
            final var requiredCRDAnn = requiredCRDsField.asNestedArray();
            requiredCRDs = new CSVMetadataHolder.RequiredCRD[requiredCRDAnn.length];
            for (int i = 0; i < requiredCRDAnn.length; i++) {
                requiredCRDs[i] = new CSVMetadataHolder.RequiredCRD(
                        ConfigurationUtils.annotationValueOrDefault(requiredCRDAnn[i], "kind",
                                AnnotationValue::asString, () -> null),
                        ConfigurationUtils.annotationValueOrDefault(requiredCRDAnn[i], "name",
                                AnnotationValue::asString, () -> null),
                        ConfigurationUtils.annotationValueOrDefault(requiredCRDAnn[i], "version",
                                AnnotationValue::asString, () -> null));
            }
        } else {
            requiredCRDs = mh.requiredCRDs;
        }

        return new CSVMetadataHolder(
                getBundleName(csvMetadata, mh.bundleName),
                ConfigurationUtils.annotationValueOrDefault(csvMetadata, "csvName",
                        AnnotationValue::asString, () -> mh.csvName),
                ConfigurationUtils.annotationValueOrDefault(csvMetadata, "description",
                        AnnotationValue::asString, () -> mh.description),
                ConfigurationUtils.annotationValueOrDefault(csvMetadata, "displayName",
                        AnnotationValue::asString, () -> mh.displayName),
                annotations,
                ConfigurationUtils.annotationValueOrDefault(csvMetadata, "keywords",
                        AnnotationValue::asStringArray, () -> mh.keywords),
                providerName, providerURL,
                ConfigurationUtils.annotationValueOrDefault(csvMetadata, "replaces",
                        AnnotationValue::asString, () -> mh.replaces),
                ConfigurationUtils.annotationValueOrDefault(csvMetadata, "skips",
                        AnnotationValue::asStringArray, () -> mh.skips),
                ConfigurationUtils.annotationValueOrDefault(csvMetadata, "version",
                        AnnotationValue::asString, () -> mh.version),
                ConfigurationUtils.annotationValueOrDefault(csvMetadata, "maturity",
                        AnnotationValue::asString, () -> mh.maturity),
                ConfigurationUtils.annotationValueOrDefault(csvMetadata, "minKubeVersion",
                        AnnotationValue::asString, () -> mh.minKubeVersion),
                maintainers,
                links,
                icon,
                installModes,
                permissionRules,
                requiredCRDs,
                origin);
    }
}
