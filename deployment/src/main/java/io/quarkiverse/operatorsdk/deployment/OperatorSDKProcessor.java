package io.quarkiverse.operatorsdk.deployment;

import static io.quarkiverse.operatorsdk.runtime.ClassUtils.loadClass;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
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
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.ControllerUtils;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.Utils;
import io.quarkiverse.operatorsdk.runtime.*;
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
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.LiveReloadBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ForceNonWeakReflectiveClassBuildItem;
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
    private static final DotName CUSTOM_RESOURCE = DotName.createSimple(CustomResource.class.getName());
    private static final DotName CONTROLLER = DotName.createSimple(Controller.class.getName());
    private static final DotName DELAY_REGISTRATION = DotName.createSimple(DelayRegistrationUntil.class.getName());

    private BuildTimeOperatorConfiguration buildTimeConfiguration;

    private final CRDGenerator generator = new CRDGenerator();

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
                        serviceBuildItem.getCRDGenerationInfo(),
                        runTimeConfiguration);
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
            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            BuildProducer<ReflectiveClassBuildItem> reflectionClasses,
            BuildProducer<ForceNonWeakReflectiveClassBuildItem> forcedReflectionClasses,
            LiveReloadBuildItem liveReload) {

        final var version = Utils.loadFromProperties();
        final CRDConfiguration crdConfig = buildTimeConfiguration.crd;
        final boolean validateCustomResources = ConfigurationUtils.shouldValidateCustomResources(
                buildTimeConfiguration.checkCRDAndValidateLocalModel, buildTimeConfiguration.crd.validate, log);

        final var index = combinedIndexBuildItem.getIndex();
        final var resourceControllers = index.getAllKnownImplementors(RESOURCE_CONTROLLER);

        final var crdGeneration = new CRDGeneration(crdConfig.generate);
        final List<QuarkusControllerConfiguration> controllerConfigs = resourceControllers.stream()
                .filter(ci -> !Modifier.isAbstract(ci.flags()))
                .map(ci -> createControllerConfiguration(ci, additionalBeans, reflectionClasses, forcedReflectionClasses,
                        index, crdGeneration, liveReload))
                .collect(Collectors.toList());

        CRDGenerationInfo crdInfo = crdGeneration.generate(outputTarget, crdConfig, validateCustomResources);
        var storedCRDInfos = liveReload.getContextObject(ContextStoredCRDInfos.class);
        if (storedCRDInfos == null) {
            storedCRDInfos = new ContextStoredCRDInfos();
        }
        storedCRDInfos.putAll(crdInfo.getCrds());
        liveReload.setContextObject(ContextStoredCRDInfos.class, storedCRDInfos); // record CRD generation info in context for future use

        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(OperatorProducer.class));
        return new ConfigurationServiceBuildItem(
                new Version(version.getSdkVersion(), version.getCommit(), version.getBuiltTime()),
                controllerConfigs,
                crdInfo);
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

            final var controllerAnnotation = info.classAnnotation(CONTROLLER);
            final var name = getControllerName(controllerClassName,
                    controllerAnnotation);

            // extract the configuration from annotation and/or external configuration
            final var configExtractor = new BuildTimeHybridControllerConfiguration(buildTimeConfiguration,
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

    private QuarkusControllerConfiguration createControllerConfiguration(
            ClassInfo info,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            BuildProducer<ReflectiveClassBuildItem> reflectionClasses,
            BuildProducer<ForceNonWeakReflectiveClassBuildItem> forcedReflectionClasses,
            IndexView index, CRDGeneration crdGeneration, LiveReloadBuildItem liveReload) {
        // first retrieve the custom resource class name
        final var crType = JandexUtil.resolveTypeParameters(info.name(), RESOURCE_CONTROLLER, index)
                .get(0)
                .name()
                .toString();

        // create ResourceController bean
        final var controllerClassName = info.name().toString();
        additionalBeans.produce(
                AdditionalBeanBuildItem.builder()
                        .addBeanClass(controllerClassName)
                        .setUnremovable()
                        .setDefaultScope(APPLICATION_SCOPED)
                        .build());

        // register CR class for introspection
        registerForReflection(reflectionClasses, crType);
        forcedReflectionClasses.produce(new ForceNonWeakReflectiveClassBuildItem(crType));

        // register spec and status for introspection
        final var crParamTypes = JandexUtil
                .resolveTypeParameters(DotName.createSimple(crType), CUSTOM_RESOURCE, index);
        registerForReflection(reflectionClasses, crParamTypes.get(0).name().toString());
        registerForReflection(reflectionClasses, crParamTypes.get(1).name().toString());

        // now check if there's more work to do, depending on reloaded state
        Class<CustomResource> crClass = null;
        String crdName = null;

        // check if we need to regenerate the CRD
        final var changeInformation = liveReload.getChangeInformation();
        if (crdGeneration.wantCRDGenerated()) {
            // check whether we already have generated CRDs
            var storedCRDInfos = liveReload.getContextObject(ContextStoredCRDInfos.class);

            final boolean[] generateCurrent = { true }; // request CRD generation by default

            crClass = (Class<CustomResource>) loadClass(crType);
            crdName = CustomResource.getCRDName(crClass);

            // When we have a live reload, check if we need to regenerate the associated CRD
            if (liveReload.isLiveReload() && storedCRDInfos != null) {
                final var finalCrdName = crdName;
                final var crdInfos = storedCRDInfos.getCRDInfosFor(crdName);

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
                crdGeneration.withCustomResource(crClass, crdName);
            }
        }

        // check if we need to regenerate the configuration for this controller
        QuarkusControllerConfiguration configuration = null;
        boolean regenerateConfig = true;
        var storedConfigurations = liveReload.getContextObject(ContextStoredControllerConfigurations.class);
        if (liveReload.isLiveReload()) {
            if (storedConfigurations != null) {
                // check if we've already generated a configuration for this controller
                configuration = storedConfigurations.getConfigurations().get(controllerClassName);
                if (configuration != null) {
                    /*
                     * A configuration needs to be regenerated if:
                     * - the ResourceController annotation has changed
                     * - the associated CustomResource metadata has changed
                     * - the configuration properties have changed as follows:
                     * + extension-wide properties affecting all controllers have changed
                     * + controller-specific properties have changed
                     *
                     * Here, we only perform a simplified check: either the class holding the ResourceController annotation has
                     * changed, or the associated CustomResource class or application.properties as a whole has changed. This
                     * could be optimized further if needed.
                     *
                     */
                    final var changedClasses = changeInformation == null ? Collections.emptySet()
                            : changeInformation.getChangedClasses();
                    regenerateConfig = changedClasses.contains(controllerClassName) || changedClasses.contains(crType)
                            || liveReload.getChangedResources().contains("application.properties");
                }
            }
        }

        if (regenerateConfig) {
            // extract the configuration from annotation and/or external configuration
            final var controllerAnnotation = info.classAnnotation(CONTROLLER);
            final var delayedRegistrationAnnotation = info.classAnnotation(DELAY_REGISTRATION);

            // retrieve the controller's name
            final String name = getControllerName(controllerClassName, controllerAnnotation);

            final var configExtractor = new BuildTimeHybridControllerConfiguration(buildTimeConfiguration,
                    buildTimeConfiguration.controllers.get(name),
                    controllerAnnotation, delayedRegistrationAnnotation);

            if (crdName == null) {
                crClass = (Class<CustomResource>) loadClass(crType);
                crdName = CustomResource.getCRDName(crClass);
            }

            // create the configuration
            configuration = new QuarkusControllerConfiguration(
                    controllerClassName,
                    name,
                    crdName,
                    configExtractor.generationAware(),
                    crType,
                    configExtractor.delayedRegistration(),
                    getNamespaces(controllerAnnotation),
                    getFinalizer(controllerAnnotation, crdName));

            log.infov(
                    "Processed ''{0}'' controller named ''{1}'' for ''{2}'' CR (version ''{3}'')",
                    controllerClassName, name, crdName, HasMetadata.getApiVersion(crClass));
        } else {
            log.infov("Skipped configuration reload for ''{0}'' controller as no changes were detected", controllerClassName);
        }

        // store the configuration in the live reload context
        if (storedConfigurations == null) {
            storedConfigurations = new ContextStoredControllerConfigurations();
        }
        storedConfigurations.getConfigurations().put(controllerClassName, configuration);
        liveReload.setContextObject(ContextStoredControllerConfigurations.class, storedConfigurations);

        return configuration;
    }

    private String getControllerName(String resourceControllerClassName, AnnotationInstance controllerAnnotation) {
        final var defaultControllerName = ControllerUtils.getDefaultResourceControllerName(resourceControllerClassName);
        return ConfigurationUtils.annotationValueOrDefault(
                controllerAnnotation, "name", AnnotationValue::asString, () -> defaultControllerName);
    }

    private Set<String> getNamespaces(AnnotationInstance controllerAnnotation) {
        return QuarkusControllerConfiguration.asSet(ConfigurationUtils.annotationValueOrDefault(
                controllerAnnotation,
                "namespaces",
                AnnotationValue::asStringArray,
                () -> new String[] {}));
    }

    private String getFinalizer(AnnotationInstance controllerAnnotation, String crdName) {
        return ConfigurationUtils.annotationValueOrDefault(controllerAnnotation,
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
}
