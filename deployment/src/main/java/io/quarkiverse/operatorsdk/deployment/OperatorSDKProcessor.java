package io.quarkiverse.operatorsdk.deployment;

import static io.quarkiverse.operatorsdk.deployment.Constants.CONTROLLER;
import static io.quarkiverse.operatorsdk.deployment.Constants.CUSTOM_RESOURCE;
import static io.quarkiverse.operatorsdk.deployment.Constants.RESOURCE_CONTROLLER;
import static io.quarkus.arc.processor.DotNames.APPLICATION_SCOPED;

import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.CDI;
import javax.inject.Singleton;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.fabric8.crd.generator.CRDGenerator;
import io.fabric8.crd.generator.CustomResourceInfo;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.ControllerUtils;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.Utils;
import io.quarkiverse.operatorsdk.runtime.BuildTimeOperatorConfiguration;
import io.quarkiverse.operatorsdk.runtime.CRDConfiguration;
import io.quarkiverse.operatorsdk.runtime.ConfigurationServiceRecorder;
import io.quarkiverse.operatorsdk.runtime.DelayRegistrationUntil;
import io.quarkiverse.operatorsdk.runtime.OperatorProducer;
import io.quarkiverse.operatorsdk.runtime.QuarkusConfigurationService;
import io.quarkiverse.operatorsdk.runtime.QuarkusControllerConfiguration;
import io.quarkiverse.operatorsdk.runtime.RunTimeOperatorConfiguration;
import io.quarkiverse.operatorsdk.runtime.Version;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.ObserverRegistrationPhaseBuildItem;
import io.quarkus.arc.deployment.ObserverRegistrationPhaseBuildItem.ObserverConfiguratorBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.arc.processor.ObserverConfigurator;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ForceNonWeakReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.deployment.util.JandexUtil;
import io.quarkus.gizmo.AssignableResultHandle;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;

class OperatorSDKProcessor {

    private static final Logger log = Logger.getLogger(OperatorSDKProcessor.class.getName());

    private static final String FEATURE = "operator-sdk";
    private static final DotName DELAY_REGISTRATION = DotName.createSimple(DelayRegistrationUntil.class.getName());
    public static final String CUSTOM_RESOURCE_DOTNAME_AS_STRING = CUSTOM_RESOURCE.toString();

    private BuildTimeOperatorConfiguration buildTimeConfiguration;

    private final CRDGenerator generator = new CRDGenerator();

    @BuildStep
    AdditionalIndexedClassesBuildItem indexExtraClasses() {
        return new AdditionalIndexedClassesBuildItem(Closeable.class.getName(), AutoCloseable.class.getName());
    }

    @BuildStep
    void setup(BuildProducer<IndexDependencyBuildItem> indexDependency,
            BuildProducer<FeatureBuildItem> features,
            BuildProducer<UnremovableBeanBuildItem> unremovableBeans) {
        features.produce(new FeatureBuildItem(FEATURE));
        indexDependency.produce(
                new IndexDependencyBuildItem("io.javaoperatorsdk", "operator-framework-core"));
        // mark ObjectMapper as non-removable
        unremovableBeans.produce(UnremovableBeanBuildItem.beanTypes(ObjectMapper.class));
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void updateControllerConfigurations(
            ConfigurationServiceRecorder recorder,
            RunTimeOperatorConfiguration runTimeConfiguration,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanBuildItemBuildProducer,
            ConfigurationServiceBuildItem serviceBuildItem) {
        final var supplier = recorder
                .configurationServiceSupplier(serviceBuildItem.getVersion(),
                        serviceBuildItem.getControllerConfigs(),
                        serviceBuildItem.isValidateCustomResources(), runTimeConfiguration);
        syntheticBeanBuildItemBuildProducer.produce(
                SyntheticBeanBuildItem.configure(QuarkusConfigurationService.class)
                        .scope(Singleton.class)
                        .addType(ConfigurationService.class)
                        .setRuntimeInit()
                        .supplier(supplier)
                        .done());
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    ConfigurationServiceBuildItem createConfigurationServiceAndOperator(
            OutputTargetBuildItem outputTarget,
            CombinedIndexBuildItem combinedIndexBuildItem,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            BuildProducer<ReflectiveClassBuildItem> reflectionClasses,
            BuildProducer<ForceNonWeakReflectiveClassBuildItem> forcedReflectionClasses,
            ConfigurationServiceRecorder recorder) {

        final var version = Utils.loadFromProperties();
        final CRDConfiguration crdConfig = buildTimeConfiguration.crd;
        final boolean validateCustomResources = shouldValidateCustomResources();

        final var index = combinedIndexBuildItem.getIndex();
        final List<QuarkusControllerConfiguration> controllerConfigs = ClassUtils.getKnownResourceControllers(index, log)
                .map(ci -> createControllerConfiguration(ci, additionalBeans, reflectionClasses, forcedReflectionClasses,
                        index))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        if (crdConfig.generate) {
            final String outputDirName = crdConfig.outputDirectory;
            final var outputDir = outputTarget.getOutputDirectory().resolve(outputDirName).toFile();
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            generator.forCRDVersions(crdConfig.versions).inOutputDir(outputDir).generate();
        }

        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(OperatorProducer.class));
        return new ConfigurationServiceBuildItem(
                new Version(version.getSdkVersion(), version.getCommit(), version.getBuiltTime()),
                controllerConfigs,
                validateCustomResources);
    }

    private boolean shouldValidateCustomResources() {
        if (Utils.isValidateCustomResourcesEnvVarSet()) {
            return Utils.shouldCheckCRDAndValidateLocalModel();
        }
        var validateCustomResources = true;
        var useDeprecated = false;
        if (buildTimeConfiguration.checkCRDAndValidateLocalModel.isPresent()) {
            validateCustomResources = buildTimeConfiguration.checkCRDAndValidateLocalModel.get();
            useDeprecated = true;
            log.warn("Use of deprecated check-crd-and-validate-local-model property. Use crd.validate instead.");
        }
        final var validate = buildTimeConfiguration.crd.validate;
        if (useDeprecated && validate != validateCustomResources) {
            log.warnv(
                    "Deprecated property check-crd-and-validate-local-model with value ''{0}'' is overridden by crd.validate property value ''{1}''",
                    validateCustomResources, validate);
            validateCustomResources = validate;
        }
        return validateCustomResources;
    }

    private boolean asBoolean(String value) {
        return Boolean.parseBoolean(value);
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
        ClassUtils.getKnownResourceControllers(index, log).forEach(info -> {
            final var controllerClassName = info.name().toString();
            final var controllerAnnotation = info.classAnnotation(CONTROLLER);
            final var name = getControllerName(controllerClassName, controllerAnnotation);

            // extract the configuration from annotation and/or external configuration
            final var configExtractor = new BuildTimeHybridControllerConfiguration(
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
                                    ResultHandle operator = getHandleFromCDI(mc, selectMethod, getMethod, cdiVar,
                                            Operator.class, null);
                                    ResultHandle resource = getHandleFromCDI(mc, selectMethod, getMethod, cdiVar,
                                            ResourceController.class, controllerClassName);
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
        });
    }

    private ResultHandle getHandleFromCDI(MethodCreator mc, MethodDescriptor selectMethod, MethodDescriptor getMethod,
            AssignableResultHandle cdiVar, Class<?> handleClass, String optionalImplClass) {
        ResultHandle operatorInstance = mc.invokeVirtualMethod(
                selectMethod,
                cdiVar,
                optionalImplClass != null ? mc.loadClass(optionalImplClass) : mc.loadClass(handleClass),
                mc.newArray(Annotation.class, 0));
        ResultHandle operator = mc.checkCast(mc.invokeInterfaceMethod(getMethod, operatorInstance), handleClass);
        return operator;
    }

    private Optional<QuarkusControllerConfiguration> createControllerConfiguration(
            ClassInfo info,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            BuildProducer<ReflectiveClassBuildItem> reflectionClasses,
            BuildProducer<ForceNonWeakReflectiveClassBuildItem> forcedReflectionClasses,
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

        // retrieve the controller's name
        final var controllerAnnotation = info.classAnnotation(CONTROLLER);
        final String name = getControllerName(resourceControllerClassName, controllerAnnotation);

        // load CR class
        final Class<CustomResource> crClass = (Class<CustomResource>) loadClass(crType);
        // if we get CustomResource instead of a subclass, ignore the controller since we cannot do anything with it
        if (crType == null || crType.equals(CUSTOM_RESOURCE_DOTNAME_AS_STRING)) {
            log.infov("Skipped processing of ''{0}'' controller as it's not parameterized with a CustomResource sub-class",
                    name);
            return Optional.empty();
        }

        generator.customResources(CustomResourceInfo.fromClass(crClass));

        // retrieve CRD name from CR type
        final var crdName = CustomResource.getCRDName(crClass);

        // register CR class for introspection
        reflectionClasses.produce(new ReflectiveClassBuildItem(true, true, crType));
        forcedReflectionClasses.produce(new ForceNonWeakReflectiveClassBuildItem(crType));

        // register spec and status for introspection
        final var crParamTypes = JandexUtil
                .resolveTypeParameters(DotName.createSimple(crType), CUSTOM_RESOURCE, index);
        registerForReflection(reflectionClasses, crParamTypes.get(0).name().toString());
        registerForReflection(reflectionClasses, crParamTypes.get(1).name().toString());

        // extract the configuration from annotation and/or external configuration
        final var delayedRegistrationAnnotation = info.classAnnotation(DELAY_REGISTRATION);

        final var configExtractor = new BuildTimeHybridControllerConfiguration(buildTimeConfiguration.controllers.get(name),
                controllerAnnotation, delayedRegistrationAnnotation);

        // create the configuration
        final var configuration = new QuarkusControllerConfiguration(
                resourceControllerClassName,
                name,
                crdName,
                configExtractor.generationAware(),
                crType,
                configExtractor.delayedRegistration(),
                getNamespaces(controllerAnnotation),
                getFinalizer(controllerAnnotation, crdName));

        log.infov(
                "Processed ''{0}'' controller named ''{1}'' for ''{2}'' CR (version ''{3}'')",
                info.name().toString(), name, crdName, HasMetadata.getApiVersion(crClass));

        return Optional.of(configuration);
    }

    private String getControllerName(String resourceControllerClassName, AnnotationInstance controllerAnnotation) {
        final var defaultControllerName = ControllerUtils.getDefaultResourceControllerName(resourceControllerClassName);
        return ValueExtractor.annotationValueOrDefault(
                controllerAnnotation, "name", AnnotationValue::asString, () -> defaultControllerName);
    }

    private Set<String> getNamespaces(AnnotationInstance controllerAnnotation) {
        return QuarkusControllerConfiguration.asSet(ValueExtractor.annotationValueOrDefault(
                controllerAnnotation,
                "namespaces",
                AnnotationValue::asStringArray,
                () -> new String[] {}));
    }

    private String getFinalizer(AnnotationInstance controllerAnnotation, String crdName) {
        return ValueExtractor.annotationValueOrDefault(controllerAnnotation,
                "finalizerName",
                AnnotationValue::asString,
                () -> ControllerUtils.getDefaultFinalizerName(crdName));
    }

    private void registerForReflection(
            BuildProducer<ReflectiveClassBuildItem> reflectionClasses, String className) {
        Optional.ofNullable(className)
                .filter(s -> !Void.TYPE.getName().equals(className))
                .ifPresent(
                        cn -> {
                            reflectionClasses.produce(new ReflectiveClassBuildItem(true, true, cn));
                            log.infov("Registered ''{0}'' for reflection", cn);
                        });
    }

    private Class<?> loadClass(String className) {
        try {
            return Thread.currentThread().getContextClassLoader().loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Couldn't find class " + className);
        }
    }
}
