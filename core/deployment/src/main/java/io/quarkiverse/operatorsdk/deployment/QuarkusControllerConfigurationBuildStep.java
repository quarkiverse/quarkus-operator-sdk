package io.quarkiverse.operatorsdk.deployment;

import static io.quarkiverse.operatorsdk.common.ClassLoadingUtils.loadClass;
import static io.quarkiverse.operatorsdk.common.Constants.*;

import java.lang.annotation.Annotation;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.jboss.jandex.*;
import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.rbac.PolicyRule;
import io.fabric8.kubernetes.api.model.rbac.PolicyRuleBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleRef;
import io.fabric8.kubernetes.api.model.rbac.RoleRefBuilder;
import io.fabric8.kubernetes.client.informers.cache.ItemStore;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.AnnotationConfigurable;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceConfigurationResolver;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.MaxReconciliationInterval;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentConverter;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import io.javaoperatorsdk.operator.processing.dependent.workflow.ManagedWorkflowSupport;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimiter;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.processing.retry.Retry;
import io.quarkiverse.operatorsdk.annotations.RBACCRoleRef;
import io.quarkiverse.operatorsdk.common.AnnotationConfigurableAugmentedClassInfo;
import io.quarkiverse.operatorsdk.common.ClassLoadingUtils;
import io.quarkiverse.operatorsdk.common.ConfigurationUtils;
import io.quarkiverse.operatorsdk.common.Constants;
import io.quarkiverse.operatorsdk.common.DependentResourceAugmentedClassInfo;
import io.quarkiverse.operatorsdk.common.ReconciledAugmentedClassInfo;
import io.quarkiverse.operatorsdk.common.ReconcilerAugmentedClassInfo;
import io.quarkiverse.operatorsdk.common.SelectiveAugmentedClassInfo;
import io.quarkiverse.operatorsdk.runtime.*;
import io.quarkiverse.operatorsdk.runtime.QuarkusBuildTimeControllerConfiguration.DefaultRateLimiter;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.LiveReloadBuildItem;
import io.quarkus.deployment.util.JandexUtil;

@SuppressWarnings("rawtypes")
class QuarkusControllerConfigurationBuildStep {

    static final Logger log = Logger.getLogger(QuarkusControllerConfigurationBuildStep.class.getName());
    private static final ManagedWorkflowSupport workflowSupport = new ManagedWorkflowSupport();

    private static final KubernetesDependentConverter KUBERNETES_DEPENDENT_CONVERTER = new KubernetesDependentConverter() {
        @Override
        @SuppressWarnings("unchecked")
        public KubernetesDependentResourceConfig configFrom(KubernetesDependent configAnnotation, DependentResourceSpec spec,
                ControllerConfiguration controllerConfig) {
            final var original = super.configFrom(configAnnotation, spec, controllerConfig);
            // make the configuration bytecode-serializable
            return new QuarkusKubernetesDependentResourceConfig(
                    original.useSSA(), original.createResourceOnlyIfNotExistingWithSSA(),
                    new QuarkusInformerConfiguration(original.informerConfig()));
        }
    };
    private static final Supplier<AnnotationInstance> NULL_ANNOTATION_SUPPLIER = () -> null;
    public static final Supplier<String[]> NULL_STRING_ARRAY_SUPPLIER = () -> null;
    public static final Supplier<String> NULL_STRING_SUPPLIER = () -> null;

    static {
        // register Quarkus-specific converter for Kubernetes dependent resources
        DependentResourceConfigurationResolver.registerConverter(KubernetesDependentResource.class,
                KUBERNETES_DEPENDENT_CONVERTER);
    }

    @BuildStep
    @SuppressWarnings("unused")
    ControllerConfigurationsBuildItem createControllerConfigurations(
            BuildTimeConfigurationServiceBuildItem buildTimeConfigurationServiceBuildItem,
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

        final List<QuarkusBuildTimeControllerConfiguration> collect = reconcilers.getReconcilers()
                .values()
                .stream()
                .map(reconcilerInfo -> {
                    // retrieve the reconciler's name
                    final var info = reconcilerInfo.classInfo();
                    final var reconcilerClassName = info.toString();

                    QuarkusBuildTimeControllerConfiguration<?> configuration = null;
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
                                buildTimeConfigurationServiceBuildItem.getConfigurationService(),
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
    static QuarkusBuildTimeControllerConfiguration createConfiguration(
            ReconcilerAugmentedClassInfo reconcilerInfo,
            Map<String, AnnotationConfigurableAugmentedClassInfo> configurableInfos,
            BuildTimeConfigurationService buildTimeConfigurationService,
            BuildTimeOperatorConfiguration buildTimeConfiguration,
            IndexView index) {

        final var info = reconcilerInfo.classInfo();
        final var reconcilerClassName = info.toString();
        final var reconcilerClass = loadClass(reconcilerClassName, Reconciler.class);
        final String name = reconcilerInfo.nameOrFailIfUnset();
        QuarkusBuildTimeControllerConfiguration<?> configuration;
        // extract the configuration from annotation and/or external configuration
        final var controllerAnnotation = info.declaredAnnotation(CONTROLLER_CONFIGURATION);

        final var externalConfiguration = buildTimeConfiguration.controllers().get(name);
        final var configExtractor = new BuildTimeHybridControllerConfiguration(buildTimeConfiguration,
                externalConfiguration,
                controllerAnnotation);

        Duration maxReconciliationInterval = null;
        OnAddFilter onAddFilter = null;
        OnUpdateFilter onUpdateFilter = null;
        GenericFilter genericFilter = null;
        Retry retry = null;
        RateLimiter rateLimiter = null;
        Long nullableInformerListLimit = null;
        String fieldManager = null;
        ItemStore<?> itemStore = null;
        Set<String> namespaces = null;
        String informerName = null;
        String labelSelector = null;
        if (controllerAnnotation != null) {
            final var intervalFromAnnotation = ConfigurationUtils.annotationValueOrDefault(
                    controllerAnnotation, "maxReconciliationInterval", AnnotationValue::asNested,
                    NULL_ANNOTATION_SUPPLIER);
            final var interval = ConfigurationUtils.annotationValueOrDefault(
                    intervalFromAnnotation, "interval", AnnotationValue::asLong,
                    () -> MaxReconciliationInterval.DEFAULT_INTERVAL);
            final var timeUnit = ConfigurationUtils.annotationValueOrDefault(
                    intervalFromAnnotation,
                    "timeUnit",
                    av -> TimeUnit.valueOf(av.asEnum()),
                    () -> TimeUnit.HOURS);
            if (interval > 0) {
                maxReconciliationInterval = Duration.of(interval, timeUnit.toChronoUnit());
            }

            // deal with informer configuration
            Class<? extends Retry> retryClass = GenericRetry.class;
            final var informerConfigAnnotation = ConfigurationUtils.annotationValueOrDefault(controllerAnnotation,
                    "informer", AnnotationValue::asNested, NULL_ANNOTATION_SUPPLIER);
            if (informerConfigAnnotation != null) {
                onAddFilter = ConfigurationUtils.instantiateImplementationClass(
                        informerConfigAnnotation, "onAddFilter", OnAddFilter.class, OnAddFilter.class, true, index);
                onUpdateFilter = ConfigurationUtils.instantiateImplementationClass(
                        informerConfigAnnotation, "onUpdateFilter", OnUpdateFilter.class, OnUpdateFilter.class,
                        true, index);
                genericFilter = ConfigurationUtils.instantiateImplementationClass(
                        informerConfigAnnotation, "genericFilter", GenericFilter.class, GenericFilter.class,
                        true, index);
                retryClass = ConfigurationUtils.annotationValueOrDefault(informerConfigAnnotation,
                        "retry", av -> loadClass(av.asClass().name().toString(), Retry.class), () -> GenericRetry.class);
                nullableInformerListLimit = ConfigurationUtils.annotationValueOrDefault(
                        informerConfigAnnotation, "informerListLimit", AnnotationValue::asLong,
                        () -> null);
                itemStore = ConfigurationUtils.instantiateImplementationClass(informerConfigAnnotation, "itemStore",
                        ItemStore.class,
                        ItemStore.class, true, index);
                informerName = ConfigurationUtils.annotationValueOrDefault(informerConfigAnnotation, "name",
                        AnnotationValue::asString, NULL_STRING_SUPPLIER);
                labelSelector = ConfigurationUtils.annotationValueOrDefault(informerConfigAnnotation,
                        "labelSelector",
                        AnnotationValue::asString,
                        NULL_STRING_SUPPLIER);

                // extract the namespaces
                // first check if we explicitly set the namespaces via the annotations
                namespaces = Optional.ofNullable(informerConfigAnnotation.value("namespaces"))
                        .map(v -> new HashSet<>(Arrays.asList(v.asStringArray())))
                        .orElse(null);
            }

            final var retryConfigurableInfo = configurableInfos.get(retryClass.getName());
            final var retryConfigurationClass = getConfigurationAnnotationClass(reconcilerInfo, retryConfigurableInfo);
            retry = configureIfNeeded(reconcilerClass, retryConfigurationClass, retryClass);

            final var rateLimiterClass = ConfigurationUtils.annotationValueOrDefault(
                    controllerAnnotation,
                    "rateLimiter", av -> loadClass(av.asClass().name().toString(), RateLimiter.class),
                    () -> DefaultRateLimiter.class);
            final var rateLimiterConfigurableInfo = configurableInfos.get(rateLimiterClass.getName());
            final var rateLimiterConfigurationClass = getConfigurationAnnotationClass(reconcilerInfo,
                    rateLimiterConfigurableInfo);
            rateLimiter = configureIfNeeded(reconcilerClass, rateLimiterConfigurationClass, rateLimiterClass);

            fieldManager = ConfigurationUtils.annotationValueOrDefault(controllerAnnotation, "fieldManager",
                    AnnotationValue::asString, NULL_STRING_SUPPLIER);
        }

        // check if we have additional RBAC rules to handle
        final var additionalRBACRules = extractAdditionalRBACRules(info);

        // check if we have additional RBAC role refs to handle
        final var additionalRBACRoleRefs = extractAdditionalRBACRoleRefs(info);

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
        final Class<? extends HasMetadata> resourceClass = (Class<? extends HasMetadata>) primaryInfo.loadAssociatedClass();
        final String resourceFullName = primaryAsResource.fullResourceName();

        final var informerConfiguration = InformerConfiguration.builder(resourceClass)
                .withName(informerName)
                .withNamespaces(namespaces)
                .withLabelSelector(labelSelector)
                .withGenericFilter(genericFilter)
                .withOnAddFilter(onAddFilter)
                .withOnUpdateFilter(onUpdateFilter)
                .withItemStore(itemStore)
                .withInformerListLimit(nullableInformerListLimit)
                .buildForController();
        final var informerConfig = new QuarkusInformerConfiguration(informerConfiguration);

        configuration = new QuarkusBuildTimeControllerConfiguration(
                reconcilerClassName,
                name,
                resourceFullName,
                configExtractor.generationAware(),
                resourceClass,
                wereNamespacesSet,
                getFinalizer(controllerAnnotation, resourceFullName),
                primaryAsResource.hasNonVoidStatus(),
                maxReconciliationInterval,
                retry, rateLimiter, additionalRBACRules, additionalRBACRoleRefs,
                fieldManager,
                informerConfig);

        // setting the configuration service with build-time known information for resolution of some information such as resource classes associated with dependent resources or whether SSA should be used for a given dependent
        configuration.setParent(buildTimeConfigurationService);

        // compute workflow and set it
        initializeWorkflowIfNeeded(configuration, reconcilerInfo, index);

        log.infov(
                "Processed ''{0}'' reconciler named ''{1}'' for ''{2}'' resource (version ''{3}'')",
                reconcilerClassName, name, resourceFullName, HasMetadata.getApiVersion(resourceClass));
        return configuration;
    }

    @SuppressWarnings("unchecked")
    private static <T> T configureIfNeeded(Class<? extends Reconciler> reconcilerClass,
            Class<? extends Annotation> configurationClass,
            Class<T> configurableClass) {
        if (configurationClass != null) {
            final var maybeConfigurable = ClassLoadingUtils.instantiate(configurableClass);
            if (maybeConfigurable instanceof AnnotationConfigurable configurable) {
                var annotation = reconcilerClass.getAnnotation(configurationClass);
                if (annotation != null) {
                    if (!DefaultRateLimiter.class.equals(configurableClass) && !GenericRetry.class.equals(configurableClass)) {
                        log.warnv(
                                "Make sure {0} (configured by {1}) follows the rules for bytecode recording (https://quarkus.io/guides/writing-extensions#bytecode-recording-2) to ensure your configuration is taken into account.",
                                configurableClass.getName(),
                                configurationClass.getName());
                    }
                    configurable.initFrom(annotation);
                }
            }
            return maybeConfigurable;
        }
        return null;
    }

    private static <R extends HasMetadata> void initializeWorkflowIfNeeded(
            QuarkusBuildTimeControllerConfiguration<R> configuration,
            ReconcilerAugmentedClassInfo reconcilerInfo, IndexView index) {
        final var workflowAnnotation = reconcilerInfo.classInfo().declaredAnnotation(WORKFLOW);
        @SuppressWarnings("unchecked")
        QuarkusManagedWorkflow<R> workflow = QuarkusManagedWorkflow.noOpManagedWorkflow;
        if (workflowAnnotation != null) {
            final var dependentResourceInfos = reconcilerInfo.getDependentResourceInfos();
            if (!dependentResourceInfos.isEmpty()) {
                Map<String, DependentResourceSpecMetadata> dependentResources = new HashMap<>(dependentResourceInfos.size());
                dependentResourceInfos.forEach(dependent -> {
                    final var spec = createDependentResourceSpec(dependent, index, configuration);
                    final var dependentName = dependent.classInfo().name();
                    dependentResources.put(dependentName.toString(), spec);
                });

                final var explicitInvocation = ConfigurationUtils.annotationValueOrDefault(
                        workflowAnnotation, "explicitInvocation", AnnotationValue::asBoolean,
                        () -> false);
                final var handleExceptionsInReconciler = ConfigurationUtils.annotationValueOrDefault(
                        workflowAnnotation, "handleExceptionsInReconciler", AnnotationValue::asBoolean,
                        () -> false);
                // make workflow bytecode serializable
                final var spec = new QuarkusWorkflowSpec(dependentResources, explicitInvocation, handleExceptionsInReconciler);
                final var original = workflowSupport.createWorkflow(spec);
                workflow = new QuarkusManagedWorkflow<>(spec, original.getOrderedSpecs(), original.hasCleaner());
            }
        }
        configuration.setWorkflow(workflow);
    }

    private static List<PolicyRule> extractAdditionalRBACRules(ClassInfo info) {
        return extractRepeatableAnnotations(info, RBAC_RULE, ADDITIONAL_RBAC_RULES,
                QuarkusControllerConfigurationBuildStep::extractRule);
    }

    private static <T> List<T> extractRepeatableAnnotations(ClassInfo info, DotName singleAnnotationName,
            DotName repeatableHolderName, Function<AnnotationInstance, T> extractor) {
        // if there are multiple annotations they should be found under an automatically generated repeatable holder annotation
        final var additionalAnnotations = ConfigurationUtils.annotationValueOrDefault(
                info.declaredAnnotation(repeatableHolderName),
                "value",
                AnnotationValue::asNestedArray,
                () -> null);
        List<T> repeatables = Collections.emptyList();
        if (additionalAnnotations != null && additionalAnnotations.length > 0) {
            repeatables = new ArrayList<>(additionalAnnotations.length);
            for (AnnotationInstance annotation : additionalAnnotations) {
                repeatables.add(extractor.apply(annotation));
            }
        }

        // if there's only one, it will be found under RBACRule annotation
        final var singleAnnotation = info.declaredAnnotation(singleAnnotationName);
        if (singleAnnotation != null) {
            repeatables = List.of(extractor.apply(singleAnnotation));
        }

        return repeatables;
    }

    private static List<RoleRef> extractAdditionalRBACRoleRefs(ClassInfo info) {
        return extractRepeatableAnnotations(info, RBAC_ROLE_REF, ADDITIONAL_RBAC_ROLE_REFS,
                QuarkusControllerConfigurationBuildStep::extractRoleRef);
    }

    private static PolicyRule extractRule(AnnotationInstance ruleAnnotation) {
        final var builder = new PolicyRuleBuilder();

        builder.withApiGroups(ConfigurationUtils.annotationValueOrDefault(ruleAnnotation,
                "apiGroups",
                AnnotationValue::asStringArray,
                NULL_STRING_ARRAY_SUPPLIER));

        builder.withVerbs(ConfigurationUtils.annotationValueOrDefault(ruleAnnotation,
                "verbs",
                AnnotationValue::asStringArray,
                NULL_STRING_ARRAY_SUPPLIER));

        builder.withResources(ConfigurationUtils.annotationValueOrDefault(ruleAnnotation,
                "resources",
                AnnotationValue::asStringArray,
                NULL_STRING_ARRAY_SUPPLIER));

        builder.withResourceNames(ConfigurationUtils.annotationValueOrDefault(ruleAnnotation,
                "resourceNames",
                AnnotationValue::asStringArray,
                NULL_STRING_ARRAY_SUPPLIER));

        builder.withNonResourceURLs(ConfigurationUtils.annotationValueOrDefault(ruleAnnotation,
                "nonResourceURLs",
                AnnotationValue::asStringArray,
                NULL_STRING_ARRAY_SUPPLIER));

        return builder.build();
    }

    private static RoleRef extractRoleRef(AnnotationInstance roleRefAnnotation) {
        final var builder = new RoleRefBuilder();

        builder.withApiGroup(RBACCRoleRef.RBAC_API_GROUP);
        builder.withKind(ConfigurationUtils.annotationValueOrDefault(roleRefAnnotation,
                "kind",
                AnnotationValue::asEnum,
                () -> null));

        builder.withName(ConfigurationUtils.annotationValueOrDefault(roleRefAnnotation,
                "name",
                AnnotationValue::asString,
                NULL_STRING_SUPPLIER));

        return builder.build();
    }

    private static Class<? extends Annotation> getConfigurationAnnotationClass(
            SelectiveAugmentedClassInfo configurationTargetInfo,
            AnnotationConfigurableAugmentedClassInfo configurableInfo) {
        if (configurableInfo != null) {
            final var associatedConfigurationClass = configurableInfo.getAssociatedConfigurationClass();
            if (configurationTargetInfo.classInfo().annotationsMap().containsKey(associatedConfigurationClass)) {
                return ClassLoadingUtils
                        .loadClass(associatedConfigurationClass.toString(), Annotation.class);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static DependentResourceSpecMetadata createDependentResourceSpec(
            DependentResourceAugmentedClassInfo dependent,
            IndexView index,
            QuarkusBuildTimeControllerConfiguration configuration) {
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
        final var resourceClass = loadClass(resourceTypeName, Object.class);

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
                NULL_STRING_SUPPLIER);

        final var spec = new DependentResourceSpecMetadata(dependentClass, dependent.nameOrFailIfUnset(),
                dependsOn, readyCondition, reconcilePrecondition, deletePostcondition, activationCondition,
                useEventSourceWithName, resourceClass);

        DependentResourceConfigurationResolver.configureSpecFromConfigured(spec, configuration, dependentClass);

        return spec;
    }

    private static String getFinalizer(AnnotationInstance controllerAnnotation, String crdName) {
        return ConfigurationUtils.annotationValueOrDefault(controllerAnnotation,
                "finalizerName",
                AnnotationValue::asString,
                () -> ReconcilerUtils.getDefaultFinalizerName(crdName));
    }
}
