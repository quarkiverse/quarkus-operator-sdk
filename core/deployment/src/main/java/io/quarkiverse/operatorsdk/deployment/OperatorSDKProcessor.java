package io.quarkiverse.operatorsdk.deployment;

import static io.quarkiverse.operatorsdk.common.ClassLoadingUtils.loadClass;
import static io.quarkiverse.operatorsdk.common.Constants.CONTROLLER_CONFIGURATION;
import static io.quarkiverse.operatorsdk.common.Constants.CUSTOM_RESOURCE;
import static io.quarkiverse.operatorsdk.common.Constants.KUBERNETES_DEPENDENT;
import static io.quarkiverse.operatorsdk.common.Constants.KUBERNETES_DEPENDENT_RESOURCE;
import static io.quarkiverse.operatorsdk.runtime.CRDUtils.applyCRD;
import static io.quarkus.arc.processor.DotNames.APPLICATION_SCOPED;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.CDI;
import javax.inject.Singleton;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.quarkiverse.operatorsdk.common.ClassUtils;
import io.quarkiverse.operatorsdk.common.ClassUtils.ReconcilerInfo;
import io.quarkiverse.operatorsdk.common.ConfigurationUtils;
import io.quarkiverse.operatorsdk.runtime.AppEventListener;
import io.quarkiverse.operatorsdk.runtime.BuildTimeOperatorConfiguration;
import io.quarkiverse.operatorsdk.runtime.CRDConfiguration;
import io.quarkiverse.operatorsdk.runtime.CRDGenerationInfo;
import io.quarkiverse.operatorsdk.runtime.ConfigurationServiceRecorder;
import io.quarkiverse.operatorsdk.runtime.DelayRegistrationUntil;
import io.quarkiverse.operatorsdk.runtime.NoOpMetricsProvider;
import io.quarkiverse.operatorsdk.runtime.OperatorProducer;
import io.quarkiverse.operatorsdk.runtime.QuarkusConfigurationService;
import io.quarkiverse.operatorsdk.runtime.QuarkusControllerConfiguration;
import io.quarkiverse.operatorsdk.runtime.QuarkusDependentResourceSpec;
import io.quarkiverse.operatorsdk.runtime.QuarkusKubernetesDependentResourceConfig;
import io.quarkiverse.operatorsdk.runtime.ResourceInfo;
import io.quarkiverse.operatorsdk.runtime.RunTimeOperatorConfiguration;
import io.quarkiverse.operatorsdk.runtime.Version;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.ObserverRegistrationPhaseBuildItem;
import io.quarkus.arc.deployment.ObserverRegistrationPhaseBuildItem.ObserverConfiguratorBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.arc.processor.ObserverConfigurator;
import io.quarkus.builder.BuildException;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.LiveReloadBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ForceNonWeakReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.metrics.MetricsCapabilityBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.deployment.util.JandexUtil;
import io.quarkus.gizmo.AssignableResultHandle;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkus.kubernetes.client.spi.KubernetesClientBuildItem;
import io.quarkus.kubernetes.spi.DecoratorBuildItem;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.metrics.MetricsFactory;

@SuppressWarnings({ "rawtypes", "unused" })
class OperatorSDKProcessor {

    static final Logger log = Logger.getLogger(OperatorSDKProcessor.class.getName());

    private static final String FEATURE = "operator-sdk";
    private static final DotName DELAY_REGISTRATION = DotName.createSimple(DelayRegistrationUntil.class.getName());

    private BuildTimeOperatorConfiguration buildTimeConfiguration;

    @BuildStep
    void setup(BuildProducer<IndexDependencyBuildItem> indexDependency,
            BuildProducer<FeatureBuildItem> features,
            BuildProducer<UnremovableBeanBuildItem> unremovableBeans,
            Optional<MetricsCapabilityBuildItem> metricsCapability,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        features.produce(new FeatureBuildItem(FEATURE));
        indexDependency.produce(
                new IndexDependencyBuildItem("io.javaoperatorsdk", "operator-framework-core"));
        // mark ObjectMapper as non-removable
        unremovableBeans.produce(UnremovableBeanBuildItem.beanTypes(ObjectMapper.class));

        // only add micrometer support if the capability is supported
        if (metricsCapability.map(m -> m.metricsSupported(MetricsFactory.MICROMETER)).orElse(false)) {
            // we use the class name to not import any micrometer-related dependencies to prevent activation
            additionalBeans.produce(
                    AdditionalBeanBuildItem.unremovableOf(
                            "io.quarkiverse.operatorsdk.runtime.MicrometerMetricsProvider"));
        } else {
            additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(NoOpMetricsProvider.class));
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void updateControllerConfigurations(
            ConfigurationServiceRecorder recorder,
            RunTimeOperatorConfiguration runTimeConfiguration,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanBuildItemBuildProducer,
            GeneratedCRDInfoBuildItem generatedCRDs,
            ConfigurationServiceBuildItem serviceBuildItem) {
        final var supplier = recorder
                .configurationServiceSupplier(serviceBuildItem.getVersion(),
                        serviceBuildItem.getControllerConfigs(),
                        generatedCRDs.getCRDGenerationInfo(),
                        runTimeConfiguration, buildTimeConfiguration);
        syntheticBeanBuildItemBuildProducer.produce(
                SyntheticBeanBuildItem.configure(QuarkusConfigurationService.class)
                        .scope(Singleton.class)
                        .addType(ConfigurationService.class)
                        .setRuntimeInit()
                        .supplier(supplier)
                        .done());
    }

    @BuildStep
    ConfigurationServiceBuildItem createConfigurationServiceAndOperator(
            OutputTargetBuildItem outputTarget,
            CombinedIndexBuildItem combinedIndexBuildItem,
            KubernetesClientBuildItem kubernetesClientBuildItem,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            BuildProducer<ReflectiveClassBuildItem> reflectionClasses,
            BuildProducer<ForceNonWeakReflectiveClassBuildItem> forcedReflectionClasses,
            BuildProducer<GeneratedCRDInfoBuildItem> generatedCRDInfo,
            LiveReloadBuildItem liveReload) {

        final CRDConfiguration crdConfig = buildTimeConfiguration.crd;
        final boolean validateCustomResources = ConfigurationUtils.shouldValidateCustomResources(
                buildTimeConfiguration.crd.validate);

        // apply should imply generate: we cannot apply if we're not generating!
        final var crdGeneration = new CRDGeneration(crdConfig.generate || crdConfig.apply);
        final var index = combinedIndexBuildItem.getIndex();
        final List<QuarkusControllerConfiguration> controllerConfigs = ClassUtils.getKnownReconcilers(index, log)
                .map(info -> createControllerConfiguration(info, additionalBeans, reflectionClasses,
                        forcedReflectionClasses,
                        index, crdGeneration, liveReload))
                .collect(Collectors.toList());

        // retrieve the known CRD information to make sure we always have a full view
        var storedCRDInfos = liveReload.getContextObject(ContextStoredCRDInfos.class);
        if (storedCRDInfos == null) {
            storedCRDInfos = new ContextStoredCRDInfos();
        }
        CRDGenerationInfo crdInfo = crdGeneration.generate(outputTarget, crdConfig,
                validateCustomResources,
                storedCRDInfos.getExisting());
        storedCRDInfos.putAll(crdInfo.getCrds());
        liveReload.setContextObject(ContextStoredCRDInfos.class,
                storedCRDInfos); // record CRD generation info in context for future use

        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(OperatorProducer.class));

        // if the app doesn't provide a main class, add the AppEventListener
        if (index.getAllKnownImplementors(DotName.createSimple(QuarkusApplication.class.getName()))
                .isEmpty()) {
            additionalBeans.produce(AdditionalBeanBuildItem.builder()
                    .addBeanClass(AppEventListener.class)
                    .setDefaultScope(DotName.createSimple(Singleton.class.getName()))
                    .setUnremovable()
                    .build());
        }

        // apply CRD if enabled
        if (crdConfig.apply) {
            for (String generatedCrdName : crdInfo.getGenerated()) {
                applyCRD(kubernetesClientBuildItem.getClient(), crdInfo, generatedCrdName);
            }
        }

        generatedCRDInfo.produce(new GeneratedCRDInfoBuildItem(crdInfo));

        return new ConfigurationServiceBuildItem(Version.loadFromProperties(), controllerConfigs);
    }

    private static class IsRBACEnabled implements BooleanSupplier {

        private BuildTimeOperatorConfiguration config;

        @Override
        public boolean getAsBoolean() {
            return !config.disableRbacGeneration;
        }
    }

    @BuildStep(onlyIf = IsRBACEnabled.class)
    @SuppressWarnings("unchecked")
    public void addRBACForResources(BuildProducer<DecoratorBuildItem> decorators,
            ConfigurationServiceBuildItem configurations) {

        final var configs = configurations.getControllerConfigs();
        final var mappings = new HashMap<String, ResourceInfo>(configs.size());
        configs.forEach((controllerName, config) -> {
            final var augmented = ResourceInfo.createFrom(config.getResourceClass(),
                    config.getResourceTypeName(),
                    controllerName, config.getSpecClassName(), config.getStatusClassName());
            mappings.put(controllerName, augmented);
        });

        decorators.produce(new DecoratorBuildItem(
                new AddClusterRolesDecorator(mappings, buildTimeConfiguration.crd.validate)));
        decorators.produce(new DecoratorBuildItem(
                new AddRoleBindingsDecorator(configs, buildTimeConfiguration.crd.validate)));
    }

    private ResultHandle getHandleFromCDI(MethodCreator mc, MethodDescriptor selectMethod,
            MethodDescriptor getMethod,
            AssignableResultHandle cdiVar, Class<?> handleClass, String optionalImplClass) {
        ResultHandle operatorInstance = mc.invokeVirtualMethod(
                selectMethod,
                cdiVar,
                optionalImplClass != null ? mc.loadClass(optionalImplClass) : mc.loadClass(handleClass),
                mc.newArray(Annotation.class, 0));
        return mc.checkCast(mc.invokeInterfaceMethod(getMethod, operatorInstance), handleClass);
    }

    @SuppressWarnings("unchecked")
    private QuarkusControllerConfiguration createControllerConfiguration(
            ReconcilerInfo reconcilerInfo,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            BuildProducer<ReflectiveClassBuildItem> reflectionClasses,
            BuildProducer<ForceNonWeakReflectiveClassBuildItem> forcedReflectionClasses,
            IndexView index, CRDGeneration crdGeneration, LiveReloadBuildItem liveReload) {
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

        // register target resource class for introspection
        registerForReflection(reflectionClasses, primaryTypeName);
        forcedReflectionClasses.produce(new ForceNonWeakReflectiveClassBuildItem(primaryTypeName));

        // register spec and status for introspection if we're targeting a CustomResource
        final var primaryCI = index.getClassByName(primaryTypeDN);
        boolean isCR = false;
        if (primaryCI == null) {
            log.warnv(
                    "''{0}'' has not been found in the Jandex index so it cannot be introspected. Assumed not to be a CustomResource implementation. If you believe this is wrong, please index your classes with Jandex.",
                    primaryTypeDN);
        } else {
            try {
                isCR = JandexUtil.isSubclassOf(index, primaryCI, CUSTOM_RESOURCE);
            } catch (BuildException e) {
                log.errorv("Couldn't ascertain if ''{0}'' is a CustomResource subclass. Assumed not to be.",
                        e);
            }
        }

        String specClassName = null;
        String statusClassName = null;
        if (isCR) {
            final var crParamTypes = JandexUtil.resolveTypeParameters(primaryTypeDN, CUSTOM_RESOURCE,
                    index);
            specClassName = crParamTypes.get(0).name().toString();
            statusClassName = crParamTypes.get(1).name().toString();
            registerForReflection(reflectionClasses, specClassName);
            registerForReflection(reflectionClasses, statusClassName);
        }

        // now check if there's more work to do, depending on reloaded state
        Class<? extends HasMetadata> resourceClass = null;
        String resourceFullName = null;

        // check if we need to regenerate the CRD
        final var changeInformation = liveReload.getChangeInformation();
        if (isCR && crdGeneration.wantCRDGenerated()) {
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
            configuration = storedConfigurations.configurationOrNullIfNeedGeneration(reconcilerClassName, changedClasses,
                    changedResources);

        }

        if (configuration == null) {
            // extract the configuration from annotation and/or external configuration
            final var delayedRegistrationAnnotation = info.classAnnotation(DELAY_REGISTRATION);
            final var controllerAnnotation = info.classAnnotation(CONTROLLER_CONFIGURATION);

            final var configExtractor = new BuildTimeHybridControllerConfiguration(buildTimeConfiguration,
                    buildTimeConfiguration.controllers.get(name),
                    controllerAnnotation, delayedRegistrationAnnotation);

            if (resourceFullName == null) {
                resourceClass = loadClass(primaryTypeName, HasMetadata.class);
                resourceFullName = getFullResourceName(resourceClass);
            }

            final var crVersion = HasMetadata.getVersion(resourceClass);

            // extract the namespaces
            final var namespaces = configExtractor.namespaces(name);

            final var dependentResources = createDependentResources(
                    reflectionClasses, index, controllerAnnotation, namespaces, additionalBeans);

            // create the configuration
            configuration = new QuarkusControllerConfiguration(
                    reconcilerClassName,
                    name,
                    resourceFullName,
                    crVersion,
                    configExtractor.generationAware(),
                    resourceClass,
                    configExtractor.delayedRegistration(),
                    namespaces,
                    getFinalizer(controllerAnnotation, resourceFullName),
                    getLabelSelector(controllerAnnotation),
                    Optional.ofNullable(specClassName),
                    Optional.ofNullable(statusClassName),
                    dependentResources);

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
            BuildProducer<ReflectiveClassBuildItem> reflectionClasses, IndexView index,
            AnnotationInstance controllerAnnotation, Set<String> namespaces,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        // deal with dependent resources
        var dependentResources = Collections.<String, QuarkusDependentResourceSpec> emptyMap();
        if (controllerAnnotation != null) {
            final var dependents = controllerAnnotation.value("dependents");
            if (dependents != null) {
                final var dependentAnnotations = dependents.asNestedArray();
                dependentResources = new HashMap<>(dependentAnnotations.length);
                for (AnnotationInstance dependentConfig : dependentAnnotations) {
                    final var dependentTypeDN = dependentConfig.value("type").asClass().name();
                    final var dependentType = index.getClassByName(dependentTypeDN);
                    if (!dependentType.hasNoArgsConstructor()) {
                        throw new IllegalArgumentException(
                                "DependentResource implementations must provide a no-arg constructor for instantiation purposes");
                    }

                    final var dependentTypeName = dependentTypeDN.toString();
                    final var dependentClass = loadClass(dependentTypeName, DependentResource.class);
                    registerForReflection(reflectionClasses, dependentTypeName);

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
                        final Set<String> dependentNamespaces = ConfigurationUtils.annotationValueOrDefault(
                                kubeDepConfig,
                                "namespaces", v -> new HashSet<>(
                                        Arrays.asList(v.asStringArray())),
                                () -> namespaces);
                        cfg = new QuarkusKubernetesDependentResourceConfig(dependentNamespaces.toArray(new String[0]),
                                labelSelector);
                    }

                    var nameField = dependentConfig.value("name");
                    final var name = Optional.ofNullable(nameField)
                            .map(AnnotationValue::asString)
                            .filter(Predicate.not(String::isBlank))
                            .orElse(DependentResource.defaultNameFor(dependentClass));
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

    private void registerForReflection(
            BuildProducer<ReflectiveClassBuildItem> reflectionClasses, String className) {
        Optional.ofNullable(className)
                .filter(s -> !className.startsWith("java."))
                .ifPresent(
                        cn -> {
                            reflectionClasses.produce(new ReflectiveClassBuildItem(true, true, cn));
                            log.infov("Registered ''{0}'' for reflection", cn);
                        });
    }

    /**
     * This looks for all resource controllers, to find those that want a delayed registration, and
     * creates one CDI observer for each, that will call operator.register on them when the event is
     * fired.
     */
    @BuildStep
    void createDelayedRegistrationObservers(
            CombinedIndexBuildItem combinedIndexBuildItem,
            ObserverRegistrationPhaseBuildItem observerRegistrationPhase,
            BuildProducer<ObserverConfiguratorBuildItem> observerConfigurators) {

        final var index = combinedIndexBuildItem.getIndex();
        ClassUtils.getKnownReconcilers(index, log).forEach(reconcilerInfo -> {
            final var info = reconcilerInfo.classInfo();
            final var controllerClassName = info.name().toString();
            final var controllerAnnotation = info.classAnnotation(CONTROLLER_CONFIGURATION);
            final var name = reconcilerInfo.name();

            // extract the configuration from annotation and/or external configuration
            final var configExtractor = new BuildTimeHybridControllerConfiguration(
                    buildTimeConfiguration,
                    buildTimeConfiguration.controllers.get(name),
                    controllerAnnotation, info.classAnnotation(DELAY_REGISTRATION));

            if (configExtractor.delayedRegistration()) {
                ObserverConfigurator configurator = observerRegistrationPhase
                        .getContext()
                        .configure()
                        .observedType(configExtractor.eventType())
                        .beanClass(info.name())
                        .notify(
                                mc -> {
                                    MethodDescriptor cdiMethod = MethodDescriptor
                                            .ofMethod(CDI.class, "current", CDI.class);
                                    MethodDescriptor selectMethod = MethodDescriptor.ofMethod(
                                            CDI.class, "select", Instance.class, Class.class,
                                            Annotation[].class);
                                    MethodDescriptor getMethod = MethodDescriptor
                                            .ofMethod(Instance.class, "get", Object.class);
                                    AssignableResultHandle cdiVar = mc.createVariable(CDI.class);
                                    mc.assign(cdiVar, mc.invokeStaticMethod(cdiMethod));
                                    ResultHandle operator = getHandleFromCDI(mc, selectMethod, getMethod,
                                            cdiVar,
                                            Operator.class, null);
                                    ResultHandle resource = getHandleFromCDI(mc, selectMethod, getMethod,
                                            cdiVar,
                                            Reconciler.class, controllerClassName);
                                    ResultHandle config = getHandleFromCDI(mc, selectMethod, getMethod,
                                            cdiVar,
                                            QuarkusConfigurationService.class, null);
                                    mc.invokeStaticMethod(
                                            MethodDescriptor.ofMethod(
                                                    OperatorProducer.class,
                                                    "applyCRDAndRegister",
                                                    void.class,
                                                    Operator.class, Reconciler.class,
                                                    QuarkusConfigurationService.class),
                                            operator, resource, config);
                                    mc.returnValue(null);
                                });
                observerConfigurators.produce(new ObserverConfiguratorBuildItem(configurator));
            }
        });
    }

}
