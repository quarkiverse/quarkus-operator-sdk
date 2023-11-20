package io.quarkiverse.operatorsdk.deployment;

import static io.quarkiverse.operatorsdk.common.ClassLoadingUtils.instantiate;
import static io.quarkiverse.operatorsdk.common.ClassLoadingUtils.loadClass;
import static io.quarkiverse.operatorsdk.common.Constants.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.jboss.jandex.*;
import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.rbac.PolicyRule;
import io.fabric8.kubernetes.api.model.rbac.PolicyRuleBuilder;
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
import io.javaoperatorsdk.operator.processing.event.rate.LinearRateLimiter;
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
import io.quarkiverse.operatorsdk.runtime.*;
import io.quarkiverse.operatorsdk.runtime.QuarkusControllerConfiguration.DefaultRateLimiter;
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
                    original.createResourceOnlyIfNotExistingWithSSA(), original.getResourceDiscriminator(),
                    (Boolean) original.useSSA().orElse(null),
                    original.onAddFilter(),
                    original.onUpdateFilter(), original.onDeleteFilter(), original.genericFilter());
        }
    };
    static {
        // register Quarkus-specific converter for Kubernetes dependent resources
        DependentResourceConfigurationResolver.registerConverter(KubernetesDependentResource.class,
                KUBERNETES_DEPENDENT_CONVERTER);
    }

    @BuildStep
    @SuppressWarnings("unused")
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

        final var externalConfiguration = buildTimeConfiguration.controllers.get(name);
        final var configExtractor = new BuildTimeHybridControllerConfiguration(buildTimeConfiguration,
                externalConfiguration,
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
        Long nullableInformerListLimit = null;
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
            // ugly hack: we currently need this because DefaultRateLimiter is not currently found as an implementor of AnnotationConfigurable
            var rateLimiterClassNameForConfigurable = rateLimiterClass.getName();
            if (DefaultRateLimiter.class.equals(rateLimiterClass)) {
                rateLimiterClassNameForConfigurable = LinearRateLimiter.class.getName();
            }
            final var rateLimiterConfigurableInfo = configurableInfos.get(rateLimiterClassNameForConfigurable);
            rateLimiterConfigurationClass = getConfigurationAnnotationClass(reconcilerInfo,
                    rateLimiterConfigurableInfo);
            nullableInformerListLimit = ConfigurationUtils.annotationValueOrDefault(
                    controllerAnnotation, "informerListLimit", AnnotationValue::asLong,
                    () -> null);
        }

        // check if we have additional RBAC rules to handle
        final var additionalRBACRules = extractAdditionalRBACRules(info);

        // extract the namespaces
        // first check if we explicitly set the namespaces via the annotations
        Set<String> namespaces = null;
        if (controllerAnnotation != null) {
            namespaces = Optional.ofNullable(controllerAnnotation.value("namespaces"))
                    .map(v -> new HashSet<>(Arrays.asList(v.asStringArray())))
                    .orElse(null);
        }
        // remember whether or not we explicitly set the namespaces
        final boolean wereNamespacesSet;
        if (namespaces == null) {
            namespaces = io.javaoperatorsdk.operator.api.reconciler.Constants.DEFAULT_NAMESPACES_SET;
            wereNamespacesSet = false;
        } else {
            wereNamespacesSet = true;
        }

        // check if we're asking to generate manifests with a specific set of namespaces
        // note that this should *NOT* be considered as explicitly setting the namespaces for the purpose of runtime overriding
        final var buildTimeNamespaces = configExtractor.generateWithWatchedNamespaces(wereNamespacesSet);
        if (buildTimeNamespaces != null) {
            namespaces = buildTimeNamespaces;
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
                nullableInformerListLimit,
                namespaces,
                wereNamespacesSet,
                getFinalizer(controllerAnnotation, resourceFullName),
                getLabelSelector(controllerAnnotation),
                primaryAsResource.hasNonVoidStatus(),
                finalFilter,
                maxReconciliationInterval,
                onAddFilter, onUpdateFilter, genericFilter, retryClass, retryConfigurationClass, rateLimiterClass,
                rateLimiterConfigurationClass, dependentResources, null, additionalRBACRules);

        if (hasDependents) {
            dependentResourceInfos.forEach(dependent -> {
                final var spec = createDependentResourceSpec(dependent, index, configuration);
                final var dependentName = dependent.classInfo().name();
                dependentResources.put(dependentName.toString(), spec);
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

    private static List<PolicyRule> extractAdditionalRBACRules(ClassInfo info) {
        // if there are multiple annotations they should be found under an automatically generated AdditionalRBACRules
        final var additionalRuleAnnotations = ConfigurationUtils.annotationValueOrDefault(
                info.declaredAnnotation(ADDITIONAL_RBAC_RULES),
                "value",
                AnnotationValue::asNestedArray,
                () -> null);
        List<PolicyRule> additionalRBACRules = Collections.emptyList();
        if (additionalRuleAnnotations != null && additionalRuleAnnotations.length > 0) {
            additionalRBACRules = new ArrayList<>(additionalRuleAnnotations.length);
            for (AnnotationInstance ruleAnnotation : additionalRuleAnnotations) {
                additionalRBACRules.add(extractRule(ruleAnnotation));
            }
        }

        // if there's only one, it will be found under RBACRule annotation
        final var rbacRuleAnnotation = info.declaredAnnotation(RBAC_RULE);
        if (rbacRuleAnnotation != null) {
            additionalRBACRules = List.of(extractRule(rbacRuleAnnotation));
        }

        return additionalRBACRules;
    }

    private static PolicyRule extractRule(AnnotationInstance ruleAnnotation) {
        final var builder = new PolicyRuleBuilder();

        builder.withApiGroups(ConfigurationUtils.annotationValueOrDefault(ruleAnnotation,
                "apiGroups",
                AnnotationValue::asStringArray,
                () -> null));

        builder.withVerbs(ConfigurationUtils.annotationValueOrDefault(ruleAnnotation,
                "verbs",
                AnnotationValue::asStringArray,
                () -> null));

        builder.withResources(ConfigurationUtils.annotationValueOrDefault(ruleAnnotation,
                "resources",
                AnnotationValue::asStringArray,
                () -> null));

        builder.withResourceNames(ConfigurationUtils.annotationValueOrDefault(ruleAnnotation,
                "resourceNames",
                AnnotationValue::asStringArray,
                () -> null));

        builder.withNonResourceURLs(ConfigurationUtils.annotationValueOrDefault(ruleAnnotation,
                "nonResourceURLs",
                AnnotationValue::asStringArray,
                () -> null));

        return builder.build();
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
        final var activationCondition = ConfigurationUtils.instantiateImplementationClass(
                dependentConfig, "activationCondition", Condition.class,
                Condition.class, true, index);

        final var useEventSourceWithName = ConfigurationUtils.annotationValueOrDefault(
                dependentConfig, "useEventSourceWithName", AnnotationValue::asString,
                () -> null);

        return new DependentResourceSpecMetadata(dependentClass, cfg, dependent.nameOrFailIfUnset(),
                dependsOn, readyCondition, reconcilePrecondition, deletePostcondition, activationCondition,
                useEventSourceWithName,
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
