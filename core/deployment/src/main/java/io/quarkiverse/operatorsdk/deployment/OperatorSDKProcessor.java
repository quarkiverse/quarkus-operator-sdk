package io.quarkiverse.operatorsdk.deployment;

import static io.quarkiverse.operatorsdk.runtime.CRDUtils.applyCRD;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import javax.inject.Singleton;

import org.jboss.jandex.DotName;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.quarkiverse.operatorsdk.common.ClassUtils;
import io.quarkiverse.operatorsdk.common.ConfigurationUtils;
import io.quarkiverse.operatorsdk.runtime.AppEventListener;
import io.quarkiverse.operatorsdk.runtime.BuildTimeOperatorConfiguration;
import io.quarkiverse.operatorsdk.runtime.CRDConfiguration;
import io.quarkiverse.operatorsdk.runtime.CRDGenerationInfo;
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
        final var generate = CRDGeneration.shouldGenerate(crdConfig.generate, crdConfig.apply, mode);
        final var crdGeneration = new CRDGeneration(generate);
        final var index = combinedIndexBuildItem.getIndex();

        final var builder = new QuarkusControllerConfigurationBuilder(additionalBeans,
                reflectionClasses, forcedReflectionClasses, index, crdGeneration, liveReload, buildTimeConfiguration);
        final var controllerConfigs = ClassUtils.getKnownReconcilers(index, log)
                .map(builder::build)
                .collect(Collectors.toList());

        // retrieve the known CRD information to make sure we always have a full view
        var storedCRDInfos = liveReload.getContextObject(ContextStoredCRDInfos.class);
        if (storedCRDInfos == null) {
            storedCRDInfos = new ContextStoredCRDInfos();
        }
        CRDGenerationInfo crdInfo = crdGeneration.generate(outputTarget, crdConfig,
                validateCustomResources,
                storedCRDInfos.getExisting(), mode);
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
        if (CRDGeneration.shouldApply(crdConfig.apply, mode)) {
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
}
