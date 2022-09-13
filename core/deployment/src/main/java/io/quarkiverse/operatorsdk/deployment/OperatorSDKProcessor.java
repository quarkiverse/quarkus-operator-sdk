package io.quarkiverse.operatorsdk.deployment;

import static io.quarkiverse.operatorsdk.runtime.CRDUtils.applyCRD;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Singleton;

import org.jboss.jandex.DotName;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.quarkiverse.operatorsdk.common.AnnotationConfigurableAugmentedClassInfo;
import io.quarkiverse.operatorsdk.common.ClassUtils;
import io.quarkiverse.operatorsdk.common.ConfigurationUtils;
import io.quarkiverse.operatorsdk.common.Constants;
import io.quarkiverse.operatorsdk.common.ResourceTargetingAugmentedClassInfo;
import io.quarkiverse.operatorsdk.common.SelectiveAugmentedClassInfo;
import io.quarkiverse.operatorsdk.runtime.AppEventListener;
import io.quarkiverse.operatorsdk.runtime.BuildTimeOperatorConfiguration;
import io.quarkiverse.operatorsdk.runtime.CRDConfiguration;
import io.quarkiverse.operatorsdk.runtime.CRDGenerationInfo;
import io.quarkiverse.operatorsdk.runtime.CRDInfo;
import io.quarkiverse.operatorsdk.runtime.ConfigurationServiceRecorder;
import io.quarkiverse.operatorsdk.runtime.NoOpMetricsProvider;
import io.quarkiverse.operatorsdk.runtime.OperatorProducer;
import io.quarkiverse.operatorsdk.runtime.QuarkusConfigurationService;
import io.quarkiverse.operatorsdk.runtime.ResourceInfo;
import io.quarkiverse.operatorsdk.runtime.RunTimeOperatorConfiguration;
import io.quarkiverse.operatorsdk.runtime.Version;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.bootstrap.app.ClassChangeInformation;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.LiveReloadBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ForceNonWeakReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.metrics.MetricsCapabilityBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.gizmo.AssignableResultHandle;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkus.kubernetes.client.spi.KubernetesClientBuildItem;
import io.quarkus.kubernetes.spi.DecoratorBuildItem;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.metrics.MetricsFactory;

@SuppressWarnings({ "unused" })
class OperatorSDKProcessor {

    static final Logger log = Logger.getLogger(OperatorSDKProcessor.class.getName());

    private static final String FEATURE = "operator-sdk";

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
            ConfigurationServiceBuildItem serviceBuildItem,
            LaunchModeBuildItem launchMode) {
        final var supplier = recorder
                .configurationServiceSupplier(serviceBuildItem.getVersion(),
                        serviceBuildItem.getControllerConfigs(),
                        generatedCRDs.getCRDGenerationInfo(),
                        runTimeConfiguration, buildTimeConfiguration, launchMode.getLaunchMode());
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
            LiveReloadBuildItem liveReload, LaunchModeBuildItem launchMode) {

        final CRDConfiguration crdConfig = buildTimeConfiguration.crd;
        final boolean validateCustomResources = ConfigurationUtils.shouldValidateCustomResources(
                buildTimeConfiguration.crd.validate);

        // apply should imply generate: we cannot apply if we're not generating!
        final var mode = launchMode.getLaunchMode();
        final var crdGeneration = new CRDGeneration(crdConfig, mode);
        final var index = combinedIndexBuildItem.getIndex();

        final var configurableInfos = ClassUtils.getProcessableImplementationsOf(Constants.ANNOTATION_CONFIGURABLE,
                index, log, Collections.emptyMap())
                .map(AnnotationConfigurableAugmentedClassInfo.class::cast)
                .collect(Collectors.toMap(ac -> ac.classInfo().name().toString(), Function.identity()));

        // retrieve the known CRD information to make sure we always have a full view
        var stored = liveReload.getContextObject(ContextStoredCRDInfos.class);
        if (stored == null) {
            stored = new ContextStoredCRDInfos();
        }
        final var storedCRDInfos = stored;

        final Set<String> changedClasses = liveReload.isLiveReload() ? Optional.ofNullable(liveReload.getChangeInformation())
                .map(ClassChangeInformation::getChangedClasses)
                .orElse(Collections.emptySet()) : Collections.emptySet();

        final var scheduledForGeneration = new HashSet<String>(7);
        final var builder = new QuarkusControllerConfigurationBuilder(additionalBeans,
                index, liveReload, buildTimeConfiguration);
        final var controllerConfigs = ClassUtils.getKnownReconcilers(index, log)
                .map(raci -> {
                    // register strongly reconciler-associated classes that need reflective access
                    registerAssociatedClassesForReflection(reflectionClasses,
                            forcedReflectionClasses, raci);

                    // add associated primary resource for CRD generation if needed
                    final var changeInformation = liveReload.getChangeInformation();
                    if (raci.isCRTargeting() && crdGeneration.wantCRDGenerated()) {
                        final var crInfo = raci.getAssociatedCustomResourceInfo();
                        // When we have a live reload, check if we need to regenerate the associated CRD
                        Map<String, CRDInfo> crdInfos = Collections.emptyMap();

                        final String targetCRName = crInfo.getAssociatedResourceTypeName();
                        if (liveReload.isLiveReload()) {
                            crdInfos = storedCRDInfos.getCRDInfosFor(targetCRName);
                        }

                        if (crdGeneration.scheduleForGenerationIfNeeded(crInfo, crdInfos, changedClasses)) {
                            scheduledForGeneration.add(targetCRName);
                        }
                    }

                    return builder.build(raci, configurableInfos);
                })
                .collect(Collectors.toList());

        // register strongly classes associated with dependent resources as well
        ClassUtils.getProcessableImplementationsOf(Constants.DEPENDENT_RESOURCE, index, log, Collections.emptyMap())
                .forEach(fci -> registerAssociatedClassesForReflection(reflectionClasses, forcedReflectionClasses, fci));

        // generate non-reconciler associated CRDs if requested
        if (crdConfig.generateAll) {
            ClassUtils.getProcessableSubClassesOf(Constants.CUSTOM_RESOURCE, index, log,
                    // pass already generated CRD names so that we can only keep the unhandled ones
                    Map.of(ResourceTargetingAugmentedClassInfo.EXISTING_CRDS_KEY, scheduledForGeneration))
                    .map(ResourceTargetingAugmentedClassInfo.class::cast)
                    .forEach(cr -> {
                        final var targetCRName = cr.getAssociatedResourceTypeName();
                        crdGeneration.withCustomResource(cr.loadAssociatedClass(), targetCRName, null);
                        log.infov("Will generate CRD for non-reconciler bound resource: {0}", targetCRName);
                    });
        }

        CRDGenerationInfo crdInfo = crdGeneration.generate(outputTarget, validateCustomResources,
                storedCRDInfos.getExisting());
        Map<String, Map<String, CRDInfo>> generatedCRDs = crdInfo.getCrds();
        storedCRDInfos.putAll(generatedCRDs);
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
        if (crdGeneration.shouldApply()) {
            for (String generatedCrdName : crdInfo.getGenerated()) {
                applyCRD(kubernetesClientBuildItem.getClient(), crdInfo, generatedCrdName);
            }
        }

        generatedCRDInfo.produce(new GeneratedCRDInfoBuildItem(crdInfo));

        return new ConfigurationServiceBuildItem(Version.loadFromProperties(), controllerConfigs);
    }

    private void registerAssociatedClassesForReflection(BuildProducer<ReflectiveClassBuildItem> reflectionClasses,
            BuildProducer<ForceNonWeakReflectiveClassBuildItem> forcedReflectionClasses,
            SelectiveAugmentedClassInfo classInfo) {
        classInfo.getClassNamesToRegisterForReflection()
                .forEach(cn -> {
                    reflectionClasses.produce(new ReflectiveClassBuildItem(true, true, cn));
                    forcedReflectionClasses.produce(
                            new ForceNonWeakReflectiveClassBuildItem(cn));
                    log.infov("Registered ''{0}'' for reflection", cn);
                });
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
                    controllerName, config.isStatusPresentAndNotVoid());
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
}
