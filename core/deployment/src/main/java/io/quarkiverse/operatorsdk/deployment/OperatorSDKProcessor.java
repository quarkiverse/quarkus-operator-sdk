package io.quarkiverse.operatorsdk.deployment;

import static io.quarkus.arc.processor.DotNames.APPLICATION_SCOPED;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.inject.Singleton;

import org.jboss.jandex.DotName;
import org.jboss.logging.Logger;

import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.LeaderElectionConfiguration;
import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.quarkiverse.operatorsdk.common.AnnotationConfigurableAugmentedClassInfo;
import io.quarkiverse.operatorsdk.common.ClassUtils;
import io.quarkiverse.operatorsdk.common.Constants;
import io.quarkiverse.operatorsdk.common.ResourceAssociatedAugmentedClassInfo;
import io.quarkiverse.operatorsdk.deployment.devui.commands.ConsoleCommands;
import io.quarkiverse.operatorsdk.runtime.*;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.*;
import io.quarkus.deployment.builditem.nativeimage.ForceNonWeakReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.metrics.MetricsCapabilityBuildItem;
import io.quarkus.gizmo.AssignableResultHandle;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.metrics.MetricsFactory;

@SuppressWarnings({ "unused" })
class OperatorSDKProcessor {

    private static final Logger log = Logger.getLogger(OperatorSDKProcessor.class.getName());

    private static final String FEATURE = "operator-sdk";
    private static final String DEFAULT_METRIC_BINDER_CLASS_NAME = "io.quarkiverse.operatorsdk.runtime.MicrometerMetricsProvider";

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

        // mark Metrics implementations as non-removable
        unremovableBeans.produce(UnremovableBeanBuildItem.beanTypes(Metrics.class));

        // mark LeaderElectionConfiguration as non-removable
        unremovableBeans.produce(UnremovableBeanBuildItem.beanTypes(LeaderElectionConfiguration.class));

        // register our Kubernetes client mapper customizer
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(KubernetesClientObjectMapperCustomizer.class));

        // register CDI qualifier for customization of the fabric8 ObjectMapper
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(KubernetesClientSerializationCustomizer.class));

        // register CDI Operator producer
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(OperatorProducer.class));

        // add default bean based on whether or not micrometer is enabled
        if (metricsCapability.map(m -> m.metricsSupported(MetricsFactory.MICROMETER)).orElse(false)) {
            // we use the class name to not import any micrometer-related dependencies to prevent activation
            additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(DEFAULT_METRIC_BINDER_CLASS_NAME));
        } else {
            additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(NoOpMetricsProvider.class));
        }

        // register health check
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(OperatorHealthCheck.class));
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    void addConsoleCommands(VersionBuildItem versionBuildItem, BuildProducer<ConsoleCommandBuildItem> commands) {
        // register dev console commands
        commands.produce(new ConsoleCommandBuildItem(new ConsoleCommands(versionBuildItem.getVersion())));
    }

    @BuildStep
    void addOperatorBoostrapIfNoApplicationClassExists(
            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            CombinedIndexBuildItem combinedIndex) {
        // if the app doesn't provide a main class, add the AppEventListener
        if (combinedIndex.getIndex().getAllKnownImplementors(DotName.createSimple(QuarkusApplication.class.getName()))
                .isEmpty()) {
            additionalBeans.produce(AdditionalBeanBuildItem.builder()
                    .addBeanClass(AppEventListener.class)
                    .setDefaultScope(DotName.createSimple(Singleton.class.getName()))
                    .setUnremovable()
                    .build());
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void updateControllerConfigurations(
            ConfigurationServiceRecorder recorder,
            RunTimeOperatorConfiguration runTimeConfiguration,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanBuildItemBuildProducer,
            GeneratedCRDInfoBuildItem generatedCRDs,
            ControllerConfigurationsBuildItem serviceBuildItem,
            VersionBuildItem versionBuildItem,
            LaunchModeBuildItem launchMode) {
        final var supplier = recorder.configurationServiceSupplier(
                versionBuildItem.getVersion(),
                serviceBuildItem.getControllerConfigs(),
                generatedCRDs.getCRDGenerationInfo(),
                runTimeConfiguration,
                buildTimeConfiguration,
                launchMode.getLaunchMode());
        syntheticBeanBuildItemBuildProducer.produce(
                SyntheticBeanBuildItem.configure(QuarkusConfigurationService.class)
                        .scope(Singleton.class)
                        .addType(ConfigurationService.class)
                        .defaultBean()
                        .setRuntimeInit()
                        .supplier(supplier)
                        .done());
    }

    @BuildStep
    ReconcilerInfosBuildItem buildReconcilerInfos(CombinedIndexBuildItem combinedIndexBuildItem,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        final var reconcilers = ClassUtils.getKnownReconcilers(combinedIndexBuildItem.getIndex(), log)
                .peek(reconcilerInfo ->
                // create Reconciler bean
                additionalBeans.produce(AdditionalBeanBuildItem.builder()
                        .addBeanClass(reconcilerInfo.classInfo().toString())
                        .setUnremovable()
                        .setDefaultScope(APPLICATION_SCOPED)
                        .build()))
                .collect(Collectors.toMap(ResourceAssociatedAugmentedClassInfo::nameOrFailIfUnset, Function.identity()));
        return new ReconcilerInfosBuildItem(reconcilers);
    }

    @BuildStep
    AnnotationConfigurablesBuildItem gatherAnnotationConfigurables(
            CombinedIndexBuildItem combinedIndexBuildItem,
            BuildProducer<QOSDKReflectiveClassBuildItem> reflectiveClassProducer) {
        final var index = combinedIndexBuildItem.getIndex();
        final var configurableInfos = ClassUtils.getProcessableImplementationsOf(
                Constants.ANNOTATION_CONFIGURABLE,
                index,
                log,
                Collections.emptyMap())
                .map(AnnotationConfigurableAugmentedClassInfo.class::cast)
                .peek(ci -> reflectiveClassProducer
                        .produce(new QOSDKReflectiveClassBuildItem(ci.getClassNamesToRegisterForReflection())))
                .collect(Collectors.toMap(ac -> ac.classInfo().name().toString(), Function.identity()));

        // register configuration targets (i.e. value of the `with` field) of Configured-annotated classes
        index.getAnnotations(Constants.CONFIGURED)
                .stream()
                .map(ai -> ai.value("with").asClass().name().toString())
                .forEach(className -> reflectiveClassProducer.produce(new QOSDKReflectiveClassBuildItem(className)));

        return new AnnotationConfigurablesBuildItem(configurableInfos);
    }

    @BuildStep
    void registerClassesForReflection(
            List<QOSDKReflectiveClassBuildItem> toRegister,
            BuildProducer<ReflectiveClassBuildItem> reflectionClasses,
            BuildProducer<ForceNonWeakReflectiveClassBuildItem> forcedReflectionClasses) {
        final var toRegisterSet = toRegister.stream()
                .flatMap(QOSDKReflectiveClassBuildItem::classNamesToRegisterForReflectionStream)
                .collect(Collectors.toSet());
        registerAssociatedClassesForReflection(reflectionClasses, forcedReflectionClasses, toRegisterSet);
    }

    @BuildStep
    void registerDependentBeans(
            ReconcilerInfosBuildItem reconcilers,
            ControllerConfigurationsBuildItem configurations,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans) {

        configurations.getControllerConfigs().values().stream()
                .filter(QuarkusControllerConfiguration::needsDependentBeansCreation)
                .forEach(config -> {
                    final var raci = reconcilers.getReconcilers().get(config.getName());
                    // register the dependent beans so that they can be found during dev mode after a restart
                    // where the dependents might not have been resolved yet
                    final var info = raci.classInfo();
                    final var reconcilerClassName = info.toString();

                    log.debugv("Created dependent beans for ''{0}'' reconciler",
                            reconcilerClassName);
                    raci.getDependentResourceInfos().forEach(dependent -> additionalBeans.produce(
                            AdditionalBeanBuildItem.builder()
                                    .addBeanClass(dependent.classInfo().name().toString())
                                    .setUnremovable()
                                    .setDefaultScope(APPLICATION_SCOPED)
                                    .build()));
                });
    }

    /**
     * create default configuration entry to ensure that env variable names will be properly mapped to what we expect. This is
     * needed because the conversion function is not a bijection i.e. there's no way to tell that OPERATOR_SDK should be mapped
     * to operator-sdk instead of operator.sdk for example.
     */
    @BuildStep
    void initializeRuntimeNamespacesFromBuildTimeValues(
            ControllerConfigurationsBuildItem configurations,
            BuildProducer<RunTimeConfigurationDefaultBuildItem> runtimeConfig) {
        configurations.getControllerConfigs().forEach((name, configuration) -> {
            @SuppressWarnings("unchecked")
            final var namespaces = String.join(",", configuration.getNamespaces());
            runtimeConfig.produce(new RunTimeConfigurationDefaultBuildItem(
                    "quarkus.operator-sdk.controllers." + configuration.getName() + ".namespaces",
                    namespaces));
        });

    }

    private void registerAssociatedClassesForReflection(BuildProducer<ReflectiveClassBuildItem> reflectionClasses,
            BuildProducer<ForceNonWeakReflectiveClassBuildItem> forcedReflectionClasses,
            Set<String> classNamesToRegister) {
        classNamesToRegister.forEach(cn -> {
            reflectionClasses.produce(ReflectiveClassBuildItem.builder(cn).methods().fields().build());
            forcedReflectionClasses.produce(
                    new ForceNonWeakReflectiveClassBuildItem(cn));
            log.infov("Registered ''{0}'' for reflection", cn);
        });
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
