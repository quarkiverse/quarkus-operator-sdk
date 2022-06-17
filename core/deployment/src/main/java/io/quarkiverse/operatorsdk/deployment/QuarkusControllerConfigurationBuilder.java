package io.quarkiverse.operatorsdk.deployment;

import static io.quarkiverse.operatorsdk.common.ClassLoadingUtils.loadClass;
import static io.quarkiverse.operatorsdk.common.Constants.CONTROLLER_CONFIGURATION;
import static io.quarkiverse.operatorsdk.common.Constants.KUBERNETES_DEPENDENT;
import static io.quarkiverse.operatorsdk.common.Constants.KUBERNETES_DEPENDENT_RESOURCE;
import static io.quarkus.arc.processor.DotNames.APPLICATION_SCOPED;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.quarkiverse.operatorsdk.common.ConfigurationUtils;
import io.quarkiverse.operatorsdk.common.ReconcilerAugmentedClassInfo;
import io.quarkiverse.operatorsdk.runtime.BuildTimeOperatorConfiguration;
import io.quarkiverse.operatorsdk.runtime.QuarkusControllerConfiguration;
import io.quarkiverse.operatorsdk.runtime.QuarkusDependentResourceSpec;
import io.quarkiverse.operatorsdk.runtime.QuarkusKubernetesDependentResourceConfig;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.builder.BuildException;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.builditem.LiveReloadBuildItem;
import io.quarkus.deployment.util.JandexUtil;

@SuppressWarnings("rawtypes")
class QuarkusControllerConfigurationBuilder {
    static final Logger log = OperatorSDKProcessor.log;

    private final BuildProducer<AdditionalBeanBuildItem> additionalBeans;
    private final IndexView index;
    private final CRDGeneration crdGeneration;
    private final LiveReloadBuildItem liveReload;

    private final BuildTimeOperatorConfiguration buildTimeConfiguration;

    public QuarkusControllerConfigurationBuilder(BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            IndexView index,
            CRDGeneration crdGeneration, LiveReloadBuildItem liveReload,
            BuildTimeOperatorConfiguration buildTimeConfiguration) {
        this.additionalBeans = additionalBeans;
        this.index = index;
        this.crdGeneration = crdGeneration;
        this.liveReload = liveReload;
        this.buildTimeConfiguration = buildTimeConfiguration;
    }

    @SuppressWarnings("unchecked")
    QuarkusControllerConfiguration build(ReconcilerAugmentedClassInfo reconcilerInfo) {
        final var primaryTypeDN = reconcilerInfo.primaryTypeName();
        final var primaryTypeName = primaryTypeDN.toString();

        // retrieve the reconciler's name
        final var info = reconcilerInfo.classInfo();
        final var reconcilerClassName = info.toString();
        final String name = reconcilerInfo.name();

        // create Reconciler bean
        additionalBeans.produce(
                AdditionalBeanBuildItem.builder()
                        .addBeanClass(reconcilerClassName)
                        .setUnremovable()
                        .setDefaultScope(APPLICATION_SCOPED)
                        .build());

        // now check if there's more work to do, depending on reloaded state
        Class<? extends HasMetadata> resourceClass = null;
        String resourceFullName = null;

        // check if we need to regenerate the CRD
        final var changeInformation = liveReload.getChangeInformation();
        if (reconcilerInfo.isCRTargeting() && crdGeneration.wantCRDGenerated()) {
            // check whether we already have generated CRDs
            var storedCRDInfos = liveReload.getContextObject(ContextStoredCRDInfos.class);

            final boolean[] generateCurrent = { true }; // request CRD generation by default

            final var crClass = loadClass(primaryTypeName, CustomResource.class);
            resourceClass = crClass;
            resourceFullName = getFullResourceName(crClass);

            // When we have a live reload, check if we need to regenerate the associated CRD
            if (liveReload.isLiveReload() && storedCRDInfos != null) {
                final var finalCrdName = resourceFullName;
                final var crdInfos = storedCRDInfos.getCRDInfosFor(resourceFullName);

                // check for all CRD spec version requested
                buildTimeConfiguration.crd.versions.forEach(v -> {
                    final var crd = crdInfos.get(v);
                    // if we don't have any information about this CRD version, we need to generate the CRD
                    if (crd == null) {
                        return;
                    }

                    // if dependent classes have been changed
                    if (changeInformation != null) {
                        for (String changedClass : changeInformation.getChangedClasses()) {
                            if (crd.getDependentClassNames().contains(changedClass)) {
                                return; // a dependent class has been changed, so we'll need to generate the CRD
                            }
                        }
                    }

                    // we've looked at all the changed classes and none have been changed for this CR/version: do not regenerate CRD
                    generateCurrent[0] = false;
                    log.infov(
                            "''{0}'' CRD generation was skipped for ''{1}'' because no changes impacting the CRD were detected",
                            v, finalCrdName);
                });
            }
            // if we still need to generate the CRD, add the CR to the set to be generated
            if (generateCurrent[0]) {
                crdGeneration.withCustomResource(crClass, resourceFullName, name);
            }
        }

        // check if we need to regenerate the configuration for this controller
        QuarkusControllerConfiguration configuration = null;
        var storedConfigurations = liveReload.getContextObject(
                ContextStoredControllerConfigurations.class);
        if (liveReload.isLiveReload() && storedConfigurations != null) {
            // check if we need to regenerate the configuration for this controller
            final var changedClasses = changeInformation == null ? Collections.<String> emptySet()
                    : changeInformation.getChangedClasses();
            final var changedResources = liveReload.getChangedResources();
            configuration = storedConfigurations.configurationOrNullIfNeedGeneration(reconcilerClassName,
                    changedClasses,
                    changedResources);

        }

        if (configuration == null) {
            // extract the configuration from annotation and/or external configuration
            final var controllerAnnotation = info.classAnnotation(CONTROLLER_CONFIGURATION);

            final var configExtractor = new BuildTimeHybridControllerConfiguration(buildTimeConfiguration,
                    buildTimeConfiguration.controllers.get(name),
                    controllerAnnotation);

            if (resourceFullName == null) {
                resourceClass = loadClass(primaryTypeName, HasMetadata.class);
                resourceFullName = getFullResourceName(resourceClass);
            }

            final var crVersion = HasMetadata.getVersion(resourceClass);

            // extract the namespaces
            final var namespaces = configExtractor.namespaces(name);

            final var dependentResources = createDependentResources(
                    index, controllerAnnotation, namespaces, additionalBeans);

            // create the configuration
            configuration = new QuarkusControllerConfiguration(
                    reconcilerClassName,
                    name,
                    resourceFullName,
                    crVersion,
                    configExtractor.generationAware(),
                    resourceClass,
                    namespaces,
                    getFinalizer(controllerAnnotation, resourceFullName),
                    getLabelSelector(controllerAnnotation),
                    reconcilerInfo.hasNonVoidStatus(),
                    dependentResources.values().stream().collect(Collectors.toUnmodifiableList()));

            log.infov(
                    "Processed ''{0}'' reconciler named ''{1}'' for ''{2}'' resource (version ''{3}'')",
                    reconcilerClassName, name, resourceFullName, HasMetadata.getApiVersion(resourceClass));
        } else {
            log.infov("Skipped configuration reload for ''{0}'' reconciler as no changes were detected",
                    reconcilerClassName);
        }

        // store the configuration in the live reload context
        if (storedConfigurations == null) {
            storedConfigurations = new ContextStoredControllerConfigurations();
        }
        storedConfigurations.recordConfiguration(configuration);
        liveReload.setContextObject(ContextStoredControllerConfigurations.class, storedConfigurations);

        return configuration;
    }

    @SuppressWarnings("unchecked")
    private Map<String, QuarkusDependentResourceSpec> createDependentResources(
            IndexView index,
            AnnotationInstance controllerAnnotation, Set<String> namespaces,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        // deal with dependent resources
        var dependentResources = Collections.<String, QuarkusDependentResourceSpec> emptyMap();
        if (controllerAnnotation != null) {
            final var dependents = controllerAnnotation.value("dependents");
            if (dependents != null) {
                final var dependentAnnotations = dependents.asNestedArray();
                dependentResources = new LinkedHashMap<>(dependentAnnotations.length);
                for (AnnotationInstance dependentConfig : dependentAnnotations) {
                    final var dependentTypeDN = dependentConfig.value("type").asClass().name();
                    final var dependentType = index.getClassByName(dependentTypeDN);
                    if (!dependentType.hasNoArgsConstructor()) {
                        throw new IllegalArgumentException(
                                "DependentResource implementations must provide a no-arg constructor for instantiation purposes");
                    }

                    final var dependentTypeName = dependentTypeDN.toString();
                    final var dependentClass = loadClass(dependentTypeName, DependentResource.class);

                    // further process Kubernetes dependents
                    final boolean isKubernetesDependent;
                    try {
                        isKubernetesDependent = JandexUtil.isSubclassOf(index, dependentType,
                                KUBERNETES_DEPENDENT_RESOURCE);
                    } catch (BuildException e) {
                        throw new IllegalStateException(
                                "DependentResource " + dependentType + " is not indexed", e);
                    }
                    Object cfg = null;
                    if (isKubernetesDependent) {
                        final var kubeDepConfig = dependentType.classAnnotation(KUBERNETES_DEPENDENT);
                        final var labelSelector = getLabelSelector(kubeDepConfig);
                        // if the dependent doesn't explicitly provide a namespace configuration, inherit the configuration from the reconciler configuration
                        final Set<String> dependentNamespaces = ConfigurationUtils.annotationValueOrDefault(
                                kubeDepConfig,
                                "namespaces", v -> new HashSet<>(
                                        Arrays.asList(v.asStringArray())),
                                () -> namespaces);
                        cfg = new QuarkusKubernetesDependentResourceConfig(dependentNamespaces, labelSelector);
                    }

                    var nameField = dependentConfig.value("name");
                    final var name = Optional.ofNullable(nameField)
                            .map(AnnotationValue::asString)
                            .filter(Predicate.not(String::isBlank))
                            .orElse(DependentResource.defaultNameFor(dependentClass));
                    final var spec = dependentResources.get(name);
                    if (spec != null) {
                        throw new IllegalArgumentException(
                                "A DependentResource named: " + name + " already exists: " + spec);
                    }
                    dependentResources.put(name, new QuarkusDependentResourceSpec(dependentClass, cfg, name));

                    additionalBeans.produce(
                            AdditionalBeanBuildItem.builder()
                                    .addBeanClass(dependentTypeName)
                                    .setUnremovable()
                                    .setDefaultScope(APPLICATION_SCOPED)
                                    .build());
                }
            }
        }
        return dependentResources;
    }

    private String getFullResourceName(Class<? extends HasMetadata> crClass) {
        return ReconcilerUtils.getResourceTypeName(crClass);
    }

    private String getFinalizer(AnnotationInstance controllerAnnotation, String crdName) {
        return ConfigurationUtils.annotationValueOrDefault(controllerAnnotation,
                "finalizerName",
                AnnotationValue::asString,
                () -> ReconcilerUtils.getDefaultFinalizerName(crdName));
    }

    private String getLabelSelector(AnnotationInstance controllerAnnotation) {
        return ConfigurationUtils.annotationValueOrDefault(controllerAnnotation,
                "labelSelector",
                AnnotationValue::asString,
                () -> null);
    }
}
