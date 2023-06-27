package io.quarkiverse.operatorsdk.deployment;

import static io.quarkiverse.operatorsdk.common.ClassLoadingUtils.instantiate;
import static io.quarkiverse.operatorsdk.common.ClassLoadingUtils.loadClass;
import static io.quarkiverse.operatorsdk.common.Constants.CONTROLLER_CONFIGURATION;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceConfigurationResolver;
import io.javaoperatorsdk.operator.api.reconciler.MaxReconciliationInterval;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentConverter;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import io.javaoperatorsdk.operator.processing.dependent.workflow.ManagedWorkflow;
import io.javaoperatorsdk.operator.processing.dependent.workflow.ManagedWorkflowFactory;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimiter;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.processing.retry.Retry;
import io.quarkiverse.operatorsdk.common.AnnotationConfigurableAugmentedClassInfo;
import io.quarkiverse.operatorsdk.common.ClassLoadingUtils;
import io.quarkiverse.operatorsdk.common.ConfigurationUtils;
import io.quarkiverse.operatorsdk.common.Constants;
import io.quarkiverse.operatorsdk.common.DependentResourceAugmentedClassInfo;
import io.quarkiverse.operatorsdk.common.ReconciledAugmentedClassInfo;
import io.quarkiverse.operatorsdk.common.ReconcilerAugmentedClassInfo;
import io.quarkiverse.operatorsdk.common.SelectiveAugmentedClassInfo;
import io.quarkiverse.operatorsdk.runtime.BuildTimeOperatorConfiguration;
import io.quarkiverse.operatorsdk.runtime.DependentResourceSpecMetadata;
import io.quarkiverse.operatorsdk.runtime.QuarkusControllerConfiguration;
import io.quarkiverse.operatorsdk.runtime.QuarkusControllerConfiguration.DefaultRateLimiter;
import io.quarkiverse.operatorsdk.runtime.QuarkusKubernetesDependentResourceConfig;
import io.quarkiverse.operatorsdk.runtime.QuarkusManagedWorkflow;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.LiveReloadBuildItem;
import io.quarkus.deployment.util.JandexUtil;

@SuppressWarnings("rawtypes")
class QuarkusControllerConfigurationBuildStep {

    static final Logger log = Logger.getLogger(QuarkusControllerConfigurationBuildStep.class.getName());

    private static final KubernetesDependentConverter KUBERNETES_DEPENDENT_CONVERTER = new KubernetesDependentConverter() {
        @Override
        @SuppressWarnings("unchecked")
        public KubernetesDependentResourceConfig configFrom(
                KubernetesDependent configAnnotation,
                ControllerConfiguration parentConfiguration, Class originatingClass) {
            final var original = super.configFrom(configAnnotation, parentConfiguration, originatingClass);
            // make the configuration bytecode-serializable
            return new QuarkusKubernetesDependentResourceConfig(original.namespaces(),
                    original.labelSelector(),
                    original.wereNamespacesConfigured(),
                    original.getResourceDiscriminator(), original.onAddFilter(),
                    original.onUpdateFilter(), original.onDeleteFilter(), original.genericFilter());
        }
    };
    static {
        // register Quarkus-specific converter for Kubernetes dependent resources
        DependentResourceConfigurationResolver.registerConverter(KubernetesDependentResource.class,
                KUBERNETES_DEPENDENT_CONVERTER);
    }

    @BuildStep
    ControllerConfigurationsBuildItem createControllerConfigurations(
            ReconcilerInfosBuildItem reconcilers,
            AnnotationConfigurablesBuildItem annotationConfigurables,
            BuildTimeOperatorConfiguration buildTimeConfiguration,
            CombinedIndexBuildItem combinedIndexBuildItem,
            LiveReloadBuildItem liveReload) {

        final var maybeStoredConfigurations = liveReload.getContextObject(ContextStoredControllerConfigurations.class);
        var storedConfigurations = maybeStoredConfigurations != null ? maybeStoredConfigurations
                : new ContextStoredControllerConfigurations();

        liveReload.setContextObject(ContextStoredControllerConfigurations.class, storedConfigurations);
        final var changedClasses = ConfigurationUtils.getChangedClasses(liveReload);
        final var changedResources = liveReload.getChangedResources();

        final List<QuarkusControllerConfiguration> collect = reconcilers.getReconcilers()
                .values()
                .stream()
                .map(reconcilerInfo -> {
                    // retrieve the reconciler's name
                    final var info = reconcilerInfo.classInfo();
                    final var reconcilerClassName = info.toString();

                    QuarkusControllerConfiguration<?> configuration = null;
                    if (liveReload.isLiveReload()) {
                        // check if we need to regenerate the configuration for this controller
                        configuration = storedConfigurations.configurationOrNullIfNeedGeneration(
                                reconcilerClassName,
                                changedClasses,
                                changedResources);
                    }

                    if (configuration == null) {
                        configuration = createConfiguration(reconcilerInfo,
                                annotationConfigurables.getConfigurableInfos(),
                                buildTimeConfiguration,
                                combinedIndexBuildItem.getIndex());
                    }

                    // store the configuration in the live reload context
                    storedConfigurations.recordConfiguration(configuration);

                    return configuration;
                }).collect(Collectors.toList());

        // store configurations in the live reload context
        liveReload.setContextObject(ContextStoredControllerConfigurations.class, storedConfigurations);

        return new ControllerConfigurationsBuildItem(collect);
    }

    @SuppressWarnings("unchecked")
    static QuarkusControllerConfiguration createConfiguration(
            ReconcilerAugmentedClassInfo reconcilerInfo,
            Map<String, AnnotationConfigurableAugmentedClassInfo> configurableInfos,
            BuildTimeOperatorConfiguration buildTimeConfiguration,
            IndexView index) {

        final var info = reconcilerInfo.classInfo();
        final var reconcilerClassName = info.toString();
        final String name = reconcilerInfo.nameOrFailIfUnset();
        QuarkusControllerConfiguration<?> configuration;
        // extract the configuration from annotation and/or external configuration
        final var controllerAnnotation = info.declaredAnnotation(CONTROLLER_CONFIGURATION);

        final var configExtractor = new BuildTimeHybridControllerConfiguration(buildTimeConfiguration,
                buildTimeConfiguration.controllers.get(name),
                controllerAnnotation);

        // deal with event filters
        ResourceEventFilter finalFilter = null;
        final var eventFilterTypes = ConfigurationUtils.annotationValueOrDefault(
                controllerAnnotation, "eventFilters",
                AnnotationValue::asClassArray, () -> new Type[0]);
        for (Type filterType : eventFilterTypes) {
            final var filterClass = loadClass(filterType.name().toString(), ResourceEventFilter.class);
            final var filter = instantiate(filterClass);
            finalFilter = finalFilter == null ? filter : finalFilter.and(filter);
        }

        Duration maxReconciliationInterval = null;
        OnAddFilter onAddFilter = null;
        OnUpdateFilter onUpdateFilter = null;
        GenericFilter genericFilter = null;
        Class<? extends Retry> retryClass = GenericRetry.class;
        Class<?> retryConfigurationClass = null;
        Class<? extends RateLimiter> rateLimiterClass = DefaultRateLimiter.class;
        Class<?> rateLimiterConfigurationClass = null;
        if (controllerAnnotation != null) {
            final var intervalFromAnnotation = ConfigurationUtils.annotationValueOrDefault(
                    controllerAnnotation, "maxReconciliationInterval", AnnotationValue::asNested,
                    () -> null);
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
            retryClass = ConfigurationUtils.annotationValueOrDefault(controllerAnnotation,
                    "retry", av -> loadClass(av.asClass().name().toString(), Retry.class), () -> GenericRetry.class);
            final var retryConfigurableInfo = configurableInfos.get(retryClass.getName());
            retryConfigurationClass = getConfigurationAnnotationClass(reconcilerInfo, retryConfigurableInfo);
            rateLimiterClass = ConfigurationUtils.annotationValueOrDefault(
                    controllerAnnotation,
                    "rateLimiter", av -> loadClass(av.asClass().name().toString(), RateLimiter.class),
                    () -> DefaultRateLimiter.class);
            final var rateLimiterConfigurableInfo = configurableInfos.get(rateLimiterClass.getName());
            rateLimiterConfigurationClass = getConfigurationAnnotationClass(reconcilerInfo,
                    rateLimiterConfigurableInfo);
        }

        // extract the namespaces
        Set<String> namespaces = configExtractor.namespaces();
        final boolean wereNamespacesSet;
        if (namespaces == null) {
            namespaces = io.javaoperatorsdk.operator.api.reconciler.Constants.DEFAULT_NAMESPACES_SET;
            wereNamespacesSet = false;
        } else {
            wereNamespacesSet = true;
        }

        // create the configuration
        final ReconciledAugmentedClassInfo<?> primaryInfo = reconcilerInfo.associatedResourceInfo();
        final var primaryAsResource = primaryInfo.asResourceTargeting();
        final var resourceClass = primaryInfo.loadAssociatedClass();
        final String resourceFullName = primaryAsResource.fullResourceName();
        // initialize dependent specs
        final Map<String, DependentResourceSpecMetadata> dependentResources;
        final var dependentResourceInfos = reconcilerInfo.getDependentResourceInfos();
        final var hasDependents = !dependentResourceInfos.isEmpty();
        if (hasDependents) {
            dependentResources = new HashMap<>(dependentResourceInfos.size());
        } else {
            dependentResources = Collections.emptyMap();
        }
        configuration = new QuarkusControllerConfiguration(
                reconcilerClassName,
                name,
                resourceFullName,
                primaryAsResource.version(),
                configExtractor.generationAware(),
                resourceClass,
                namespaces,
                wereNamespacesSet,
                getFinalizer(controllerAnnotation, resourceFullName),
                getLabelSelector(controllerAnnotation),
                primaryAsResource.hasNonVoidStatus(),
                finalFilter,
                maxReconciliationInterval,
                onAddFilter, onUpdateFilter, genericFilter, retryClass, retryConfigurationClass,
                rateLimiterClass,
                rateLimiterConfigurationClass, dependentResources, null);

        if (hasDependents) {
            dependentResourceInfos.forEach(dependent -> {
                final var spec = createDependentResourceSpec(dependent, index, configuration);
                final var dependentName = dependent.classInfo().name();
                dependentResources.put(dependentName.toString(), spec);

                //                final var dependentTypeName = dependentName.toString();
                //                additionalBeans.produce(
                //                        AdditionalBeanBuildItem.builder()
                //                                .addBeanClass(dependentTypeName)
                //                                .setUnremovable()
                //                                .setDefaultScope(APPLICATION_SCOPED)
                //                                .build());
            });
        }

        // compute workflow and set it (originally set to null in constructor)
        final ManagedWorkflow workflow;
        if (hasDependents) {
            // make workflow bytecode serializable
            final var original = ManagedWorkflowFactory.DEFAULT.workflowFor(configuration);
            workflow = new QuarkusManagedWorkflow<>(original.getOrderedSpecs(),
                    original.hasCleaner());
        } else {
            workflow = QuarkusManagedWorkflow.noOpManagedWorkflow;
        }
        configuration.setWorkflow(workflow);

        log.infov(
                "Processed ''{0}'' reconciler named ''{1}'' for ''{2}'' resource (version ''{3}'')",
                reconcilerClassName, name, resourceFullName, HasMetadata.getApiVersion(resourceClass));
        return configuration;
    }

    private static Class<?> getConfigurationAnnotationClass(SelectiveAugmentedClassInfo configurationTargetInfo,
            AnnotationConfigurableAugmentedClassInfo configurableInfo) {
        if (configurableInfo != null) {
            final var associatedConfigurationClass = configurableInfo.getAssociatedConfigurationClass();
            if (configurationTargetInfo.classInfo().annotationsMap().containsKey(associatedConfigurationClass)) {
                return ClassLoadingUtils
                        .loadClass(associatedConfigurationClass.toString(), Object.class);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static DependentResourceSpecMetadata createDependentResourceSpec(
            DependentResourceAugmentedClassInfo dependent,
            IndexView index,
            QuarkusControllerConfiguration configuration) {
        final var dependentResourceType = dependent.classInfo();

        // resolve the associated resource type
        final var drTypeName = dependentResourceType.name();
        final var types = JandexUtil.resolveTypeParameters(drTypeName, Constants.DEPENDENT_RESOURCE,
                index);
        final String resourceTypeName;
        if (types.size() == 2) {
            resourceTypeName = types.get(0).name().toString();
        } else {
            throw new IllegalArgumentException(
                    "Improperly parameterized DependentResource implementation: " + drTypeName.toString());
        }

        final var dependentTypeName = drTypeName.toString();
        final var dependentClass = loadClass(dependentTypeName, DependentResource.class);

        final var cfg = DependentResourceConfigurationResolver.extractConfigurationFromConfigured(
                dependentClass, configuration);

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
        final var useEventSourceWithName = ConfigurationUtils.annotationValueOrDefault(
                dependentConfig, "useEventSourceWithName", AnnotationValue::asString,
                () -> null);

        return new DependentResourceSpecMetadata(dependentClass, cfg, dependent.nameOrFailIfUnset(),
                dependsOn, readyCondition, reconcilePrecondition, deletePostcondition, useEventSourceWithName,
                resourceTypeName);

    }

    private static String getFinalizer(AnnotationInstance controllerAnnotation, String crdName) {
        return ConfigurationUtils.annotationValueOrDefault(controllerAnnotation,
                "finalizerName",
                AnnotationValue::asString,
                () -> ReconcilerUtils.getDefaultFinalizerName(crdName));
    }

    private static String getLabelSelector(AnnotationInstance controllerAnnotation) {
        return ConfigurationUtils.annotationValueOrDefault(controllerAnnotation,
                "labelSelector",
                AnnotationValue::asString,
                () -> null);
    }
}
