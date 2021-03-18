package io.quarkiverse.operatorsdk.deployment;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.CDI;
import javax.inject.Singleton;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.RetryConfiguration;
import io.javaoperatorsdk.operator.api.config.Utils;
import io.quarkiverse.operatorsdk.runtime.ConfigurationServiceRecorder;
import io.quarkiverse.operatorsdk.runtime.ExternalConfiguration;
import io.quarkiverse.operatorsdk.runtime.ExternalControllerConfiguration;
import io.quarkiverse.operatorsdk.runtime.OperatorProducer;
import io.quarkiverse.operatorsdk.runtime.QuarkusConfigurationService;
import io.quarkiverse.operatorsdk.runtime.QuarkusControllerConfiguration;
import io.quarkiverse.operatorsdk.runtime.Version;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.ObserverRegistrationPhaseBuildItem;
import io.quarkus.arc.deployment.ObserverRegistrationPhaseBuildItem.ObserverConfiguratorBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.processor.ObserverConfigurator;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.deployment.util.JandexUtil;
import io.quarkus.gizmo.AssignableResultHandle;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;

class OperatorSDKProcessor {

    private static final Logger log = Logger.getLogger(OperatorSDKProcessor.class.getName());

    private static final String FEATURE = "operator-sdk";
    private static final DotName RESOURCE_CONTROLLER = DotName
            .createSimple(ResourceController.class.getName());

    private static final DotName APPLICATION_SCOPED = DotName
            .createSimple(ApplicationScoped.class.getName());

    private ExternalConfiguration externalConfiguration;

    // todo: reactivate when CRD generation from API works properly
    //    private final CRDGenerator generator = new CRDGenerator();

    @BuildStep
    void indexSDKDependencies(
            BuildProducer<IndexDependencyBuildItem> indexDependency,
            BuildProducer<FeatureBuildItem> features) {
        features.produce(new FeatureBuildItem(FEATURE));
        indexDependency.produce(
                new IndexDependencyBuildItem("io.javaoperatorsdk", "operator-framework-core"));
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void createConfigurationServiceAndOperator(
            OutputTargetBuildItem outputTarget,
            CombinedIndexBuildItem combinedIndexBuildItem,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanBuildItemBuildProducer,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            BuildProducer<ReflectiveClassBuildItem> reflectionClasses,
            ConfigurationServiceRecorder recorder) {
        final var index = combinedIndexBuildItem.getIndex();
        final var resourceControllers = index.getAllKnownImplementors(RESOURCE_CONTROLLER);

        final List<ControllerConfiguration> controllerConfigs = resourceControllers.stream()
                .filter(ci -> !Modifier.isAbstract(ci.flags()))
                .map(ci -> createControllerConfiguration(ci, additionalBeans, reflectionClasses, index))
                .collect(Collectors.toList());

        // todo: reactivate when CRD generation from API works properly
        /*
         * // generate CRDs
         * final var outputDir = outputTarget.getOutputDirectory().resolve(externalConfiguration.crdOutputDirectory).toFile();
         * if (!outputDir.exists()) {
         * outputDir.mkdirs();
         * }
         * generator.inOutputDir(outputDir).generate();
         */

        final var version = Utils.loadFromProperties();
        final var validateCustomResources = Utils.isValidateCustomResourcesEnvVarSet()
                ? Utils.shouldCheckCRDAndValidateLocalModel()
                : externalConfiguration.checkCRDAndValidateLocalModel.orElse(true);

        final var supplier = recorder.configurationServiceSupplier(
                new Version(version.getSdkVersion(), version.getCommit(), version.getBuiltTime()),
                controllerConfigs,
                validateCustomResources);
        syntheticBeanBuildItemBuildProducer.produce(
                SyntheticBeanBuildItem.configure(QuarkusConfigurationService.class)
                        .scope(Singleton.class)
                        .addType(ConfigurationService.class)
                        .setRuntimeInit()
                        .supplier(supplier)
                        .done());

        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(OperatorProducer.class));
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
        for (ClassInfo info : index.getAllKnownImplementors(RESOURCE_CONTROLLER)) {
            final var controllerClassName = info.name().toString();

            // extract the configuration from annotation and/or external configuration
            final var configExtractor = new HybridControllerConfiguration(
                    controllerClassName, externalConfiguration, info);

            if (configExtractor.delayedRegistration()) {
                ObserverConfigurator configurator = observerRegistrationPhase
                        .getContext()
                        .configure()
                        .observedType(configExtractor.eventType())
                        .beanClass(
                                DotName.createSimple(controllerClassName + "_registration_observer"))
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
                                    ResultHandle operatorInstance = mc.invokeVirtualMethod(
                                            selectMethod,
                                            cdiVar,
                                            mc.loadClass(Operator.class),
                                            mc.newArray(Annotation.class, 0));
                                    ResultHandle operator = mc.checkCast(
                                            mc.invokeInterfaceMethod(getMethod, operatorInstance),
                                            Operator.class);
                                    ResultHandle resourceInstance = mc.invokeVirtualMethod(
                                            selectMethod,
                                            cdiVar,
                                            mc.loadClass(controllerClassName),
                                            mc.newArray(Annotation.class, 0));
                                    ResultHandle resource = mc.checkCast(
                                            mc.invokeInterfaceMethod(getMethod, resourceInstance),
                                            ResourceController.class);
                                    mc.invokeVirtualMethod(
                                            MethodDescriptor.ofMethod(
                                                    Operator.class, "register", void.class,
                                                    ResourceController.class),
                                            operator,
                                            resource);
                                    mc.returnValue(null);
                                });
                observerConfigurators.produce(new ObserverConfiguratorBuildItem(configurator));
            }
        }
    }

    private ControllerConfiguration createControllerConfiguration(
            ClassInfo info,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            BuildProducer<ReflectiveClassBuildItem> reflectionClasses,
            IndexView index) {
        // first retrieve the custom resource class
        final var crType = JandexUtil.resolveTypeParameters(info.name(), RESOURCE_CONTROLLER, index)
                .get(0)
                .name()
                .toString();

        // create ResourceController bean
        final var resourceControllerClassName = info.name().toString();
        additionalBeans.produce(
                AdditionalBeanBuildItem.builder()
                        .addBeanClass(resourceControllerClassName)
                        .setUnremovable()
                        .setDefaultScope(APPLICATION_SCOPED)
                        .build());

        // load CR class
        final Class<CustomResource> crClass = (Class<CustomResource>) loadClass(crType);

        // todo: reactivate when CRD generation from API works properly
        // process the CR to generate the CRD
        //        generator.customResources(CustomResourceInfo.fromClass(crClass));

        // Instantiate CR to check that it's properly annotated
        final CustomResource cr;
        try {
            cr = crClass.getConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot instantiate '" + crType + "' CR class", e);
        }

        // retrieve CRD name from CR type
        final var crdName = CustomResource.getCRDName(crClass);

        // register CR class for introspection
        reflectionClasses.produce(new ReflectiveClassBuildItem(true, true, crType));

        // register spec and status for introspection
        registerForReflection(reflectionClasses, cr.getSpec());
        registerForReflection(reflectionClasses, cr.getStatus());

        // extract the configuration from annotation and/or external configuration
        final var configExtractor = new HybridControllerConfiguration(resourceControllerClassName,
                externalConfiguration,
                info);

        // create the configuration
        final var name = configExtractor.name();

        // create the configuration
        final var configuration = new QuarkusControllerConfiguration(
                resourceControllerClassName,
                name,
                crdName,
                configExtractor.finalizer(crdName),
                configExtractor.generationAware(),
                QuarkusControllerConfiguration.asSet(configExtractor.namespaces()),
                crType,
                configExtractor.retryConfiguration(),
                configExtractor.delayedRegistration());

        log.infov(
                "Processed ''{0}'' controller named ''{1}'' for ''{2}'' CR (version ''{3}'')",
                info.name().toString(), name, cr.getCRDName(), cr.getApiVersion());

        return configuration;
    }

    private void registerForReflection(
            BuildProducer<ReflectiveClassBuildItem> reflectionClasses, Object specOrStatus) {
        Optional.ofNullable(specOrStatus)
                .map(s -> specOrStatus.getClass().getCanonicalName())
                .ifPresent(
                        cn -> {
                            reflectionClasses.produce(new ReflectiveClassBuildItem(true, true, cn));
                            System.out.println("Registered " + cn);
                        });
    }

    private RetryConfiguration retryConfiguration(ExternalControllerConfiguration extConfig) {
        return extConfig == null ? null : RetryConfigurationResolver.resolve(extConfig.retry);
    }

    private Class<?> loadClass(String className) {
        try {
            return Thread.currentThread().getContextClassLoader().loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Couldn't find class " + className);
        }
    }
}
