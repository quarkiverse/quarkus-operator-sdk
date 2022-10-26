package io.quarkiverse.operatorsdk.deployment;

import static io.quarkiverse.operatorsdk.common.ClassLoadingUtils.instantiate;
import static io.quarkiverse.operatorsdk.common.ClassLoadingUtils.loadClass;
import static io.quarkiverse.operatorsdk.common.Constants.CONTROLLER_CONFIGURATION;
import static io.quarkiverse.operatorsdk.common.Constants.KUBERNETES_DEPENDENT;
import static io.quarkiverse.operatorsdk.common.Constants.KUBERNETES_DEPENDENT_RESOURCE;
import static io.quarkus.arc.processor.DotNames.APPLICATION_SCOPED;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.MaxReconciliationInterval;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimiter;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnDeleteFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.processing.retry.Retry;
import io.quarkiverse.operatorsdk.common.AnnotationConfigurableAugmentedClassInfo;
import io.quarkiverse.operatorsdk.common.ClassLoadingUtils;
import io.quarkiverse.operatorsdk.common.ConfigurationUtils;
import io.quarkiverse.operatorsdk.common.DependentResourceAugmentedClassInfo;
import io.quarkiverse.operatorsdk.common.ReconciledAugmentedClassInfo;
import io.quarkiverse.operatorsdk.common.ReconcilerAugmentedClassInfo;
import io.quarkiverse.operatorsdk.runtime.BuildTimeOperatorConfiguration;
import io.quarkiverse.operatorsdk.runtime.QuarkusControllerConfiguration;
import io.quarkiverse.operatorsdk.runtime.QuarkusControllerConfiguration.DefaultRateLimiter;
import io.quarkiverse.operatorsdk.runtime.QuarkusDependentResourceSpec;
import io.quarkiverse.operatorsdk.runtime.QuarkusKubernetesDependentResourceConfig;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.builder.BuildException;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.builditem.LiveReloadBuildItem;
import io.quarkus.deployment.util.JandexUtil;

@SuppressWarnings("rawtypes")
class QuarkusControllerConfigurationBuilder {

    static final Logger log = Logger.getLogger(QuarkusControllerConfigurationBuilder.class.getName());

    private final BuildProducer<AdditionalBeanBuildItem> additionalBeans;
    private final IndexView index;
    private final LiveReloadBuildItem liveReload;

    private final BuildTimeOperatorConfiguration buildTimeConfiguration;

    public QuarkusControllerConfigurationBuilder(
            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            IndexView index, LiveReloadBuildItem liveReload,
            BuildTimeOperatorConfiguration buildTimeConfiguration) {
        this.additionalBeans = additionalBeans;
        this.index = index;
        this.liveReload = liveReload;
        this.buildTimeConfiguration = buildTimeConfiguration;
    }

    @SuppressWarnings("unchecked")
    QuarkusControllerConfiguration build(ReconcilerAugmentedClassInfo reconcilerInfo,
            Map<String, AnnotationConfigurableAugmentedClassInfo> configurableInfos) {

        // retrieve the reconciler's name
        final var info = reconcilerInfo.classInfo();
        final var reconcilerClassName = info.toString();
        final String name = reconcilerInfo.nameOrFailIfUnset();

        // create Reconciler bean
        additionalBeans.produce(
                AdditionalBeanBuildItem.builder()
                        .addBeanClass(reconcilerClassName)
                        .setUnremovable()
                        .setDefaultScope(APPLICATION_SCOPED)
                        .build());

        // check if we need to regenerate the configuration for this controller
        final var changeInformation = liveReload.getChangeInformation();
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

            // deal with event filters
            ResourceEventFilter finalFilter = null;
            final var eventFilterTypes = ConfigurationUtils.annotationValueOrDefault(
                    controllerAnnotation, "eventFilters",
                    AnnotationValue::asClassArray, () -> new Type[0]);
            for (Type filterType : eventFilterTypes) {
                final var filterClass = loadClass(filterType.name().toString(),
                        ResourceEventFilter.class);
                final var filter = instantiate(filterClass);
                finalFilter = finalFilter == null ? filter : finalFilter.and(filter);
            }

            Duration maxReconciliationInterval = null;
            OnAddFilter onAddFilter = null;
            OnUpdateFilter onUpdateFilter = null;
            GenericFilter genericFilter = null;
            Retry retry = null;
            Class<?> retryConfigurationClass = null;
            RateLimiter rateLimiter = null;
            Class<?> rateLimiterConfigurationClass = null;
            if (controllerAnnotation != null) {
                final var intervalFromAnnotation = ConfigurationUtils.annotationValueOrDefault(
                        controllerAnnotation, "maxReconciliationInterval", AnnotationValue::asNested,
                        () -> null);
                // todo: use default value constant when available
                final var interval = ConfigurationUtils.annotationValueOrDefault(
                        intervalFromAnnotation, "interval", AnnotationValue::asLong,
                        () -> MaxReconciliationInterval.DEFAULT_INTERVAL);
                final var timeUnit = (TimeUnit) ConfigurationUtils.annotationValueOrDefault(
                        intervalFromAnnotation,
                        "timeUnit",
                        av -> TimeUnit.valueOf(av.asEnum()),
                        () -> TimeUnit.HOURS);
                if (interval > 0) {
                    maxReconciliationInterval = Duration.of(interval, timeUnit.toChronoUnit());
                }

                onAddFilter = ConfigurationUtils.instantiateImplementationClass(
                        controllerAnnotation, "onAddFilter", OnAddFilter.class, OnAddFilter.class, true, index);
                onUpdateFilter = ConfigurationUtils.instantiateImplementationClass(
                        controllerAnnotation, "onUpdateFilter", OnUpdateFilter.class, OnUpdateFilter.class,
                        true, index);
                genericFilter = ConfigurationUtils.instantiateImplementationClass(
                        controllerAnnotation, "genericFilter", GenericFilter.class, GenericFilter.class,
                        true, index);
                retry = ConfigurationUtils.instantiateImplementationClass(
                        controllerAnnotation, "retry", Retry.class, GenericRetry.class,
                        false, index);
                assert retry != null;
                final var retryConfigurableInfo = configurableInfos.get(retry.getClass().getName());
                retryConfigurationClass = getConfigurationClass(reconcilerInfo, retryConfigurableInfo);
                rateLimiter = ConfigurationUtils.instantiateImplementationClass(
                        controllerAnnotation, "rateLimiter", RateLimiter.class, DefaultRateLimiter.class,
                        false, index);
                assert rateLimiter != null;
                final var rateLimiterConfigurableInfo = configurableInfos.get(
                        rateLimiter.getClass().getName());
                rateLimiterConfigurationClass = getConfigurationClass(reconcilerInfo,
                        rateLimiterConfigurableInfo);
            }

            // extract the namespaces
            final var namespaces = configExtractor.namespaces(name);

            final var dependentResourceInfos = reconcilerInfo.getDependentResourceInfos();
            final List<DependentResourceSpec> dependentResources;
            if (!dependentResourceInfos.isEmpty()) {
                dependentResources = new ArrayList<>(dependentResourceInfos.size());
                dependentResourceInfos.forEach(dependent -> {
                    dependentResources.add(createDependentResourceSpec(dependent, index, namespaces));

                    final var dependentTypeName = dependent.classInfo().name().toString();
                    additionalBeans.produce(
                            AdditionalBeanBuildItem.builder()
                                    .addBeanClass(dependentTypeName)
                                    .setUnremovable()
                                    .setDefaultScope(APPLICATION_SCOPED)
                                    .build());
                });
            } else {
                dependentResources = Collections.emptyList();
            }

            // create the configuration
            final ReconciledAugmentedClassInfo<?> primaryInfo = reconcilerInfo.associatedResourceInfo();
            final var primaryAsResource = primaryInfo.asResourceTargeting();
            final var resourceClass = primaryInfo.loadAssociatedClass();
            final String resourceFullName = primaryAsResource.fullResourceName();
            configuration = new QuarkusControllerConfiguration(
                    reconcilerClassName,
                    name,
                    resourceFullName,
                    primaryAsResource.version(),
                    configExtractor.generationAware(),
                    resourceClass,
                    namespaces,
                    getFinalizer(controllerAnnotation, resourceFullName),
                    getLabelSelector(controllerAnnotation),
                    primaryAsResource.hasNonVoidStatus(),
                    dependentResources,
                    finalFilter,
                    maxReconciliationInterval,
                    onAddFilter, onUpdateFilter, genericFilter, retry, retryConfigurationClass, rateLimiter,
                    rateLimiterConfigurationClass);

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

    private static Class<?> getConfigurationClass(ReconcilerAugmentedClassInfo reconcilerInfo,
            AnnotationConfigurableAugmentedClassInfo configurableInfo) {
        if (configurableInfo != null) {
            final var associatedConfigurationClass = configurableInfo.getAssociatedConfigurationClass();
            // Keeping the deprecated method due to compatibility with Quarkus 2.7.
            if (reconcilerInfo.classInfo().annotations().containsKey(associatedConfigurationClass)) {
                return ClassLoadingUtils
                        .loadClass(associatedConfigurationClass.toString(), Object.class);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private QuarkusDependentResourceSpec createDependentResourceSpec(
            DependentResourceAugmentedClassInfo dependent,
            IndexView index,
            Set<String> namespaces) {
        final var dependentType = dependent.classInfo();

        final var dependentTypeName = dependentType.name().toString();

        // further process Kubernetes dependents
        final boolean isKubernetesDependent;
        try {
            isKubernetesDependent = JandexUtil.isSubclassOf(index, dependentType,
                    KUBERNETES_DEPENDENT_RESOURCE);
        } catch (BuildException e) {
            throw new IllegalStateException("DependentResource " + dependentType + " is not indexed", e);
        }
        Object cfg = null;
        if (isKubernetesDependent) {
            final var kubeDepConfig = dependentType.classAnnotation(KUBERNETES_DEPENDENT);
            final var labelSelector = getLabelSelector(kubeDepConfig);
            // if the dependent doesn't explicitly provide a namespace configuration, inherit the configuration from the reconciler configuration
            var dependentNamespaces = namespaces;
            var configuredNS = false;
            if (kubeDepConfig != null) {
                // if the KubernetesDependent provides an explicit namespaces configuration, use that instead of the namespaces inherited from reconciler
                final var nonDefaultNS = Optional.ofNullable(
                        kubeDepConfig.value("namespaces"))
                        .map(AnnotationValue::asStringArray)
                        .filter(v -> !Arrays.equals(KubernetesDependent.DEFAULT_NAMESPACES, v));
                if (nonDefaultNS.isPresent()) {
                    configuredNS = true;
                    dependentNamespaces = nonDefaultNS.map(Set::of).orElse(namespaces);
                }
            }
            final var onAddFilter = ConfigurationUtils.instantiateImplementationClass(
                    kubeDepConfig, "onAddFilter", OnAddFilter.class,
                    OnAddFilter.class, true, index);
            final var onUpdateFilter = ConfigurationUtils.instantiateImplementationClass(
                    kubeDepConfig, "onUpdateFilter", OnUpdateFilter.class,
                    OnUpdateFilter.class, true,
                    index);
            final var onDeleteFilter = ConfigurationUtils.instantiateImplementationClass(
                    kubeDepConfig, "onDeleteFilter", OnDeleteFilter.class,
                    OnDeleteFilter.class, true,
                    index);
            final var genericFilter = ConfigurationUtils.instantiateImplementationClass(
                    kubeDepConfig, "genericFilter", GenericFilter.class,
                    GenericFilter.class, true,
                    index);

            cfg = new QuarkusKubernetesDependentResourceConfig(dependentNamespaces, labelSelector,
                    configuredNS,
                    onAddFilter, onUpdateFilter,
                    onDeleteFilter, genericFilter);
        }

        final var dependentClass = loadClass(dependentTypeName,
                DependentResource.class);

        final var dependentConfig = dependent.getDependentAnnotationFromController();
        final var dependsOnField = dependentConfig.value("dependsOn");
        final var dependsOn = Optional.ofNullable(dependsOnField)
                .map(AnnotationValue::asStringArray)
                .filter(array -> array.length > 0)
                .map(Set::of).orElse(Collections.emptySet());

        final var readyCondition = ConfigurationUtils.instantiateImplementationClass(
                dependentConfig, "readyPostcondition", Condition.class,
                Condition.class, true, index);
        final var reconcilePrecondition = ConfigurationUtils.instantiateImplementationClass(
                dependentConfig, "reconcilePrecondition", Condition.class,
                Condition.class, true, index);
        final var deletePostcondition = ConfigurationUtils.instantiateImplementationClass(
                dependentConfig, "deletePostcondition", Condition.class,
                Condition.class, true, index);

        return new QuarkusDependentResourceSpec(dependentClass, cfg, dependent.nameOrFailIfUnset(),
                dependsOn, readyCondition, reconcilePrecondition, deletePostcondition);

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
