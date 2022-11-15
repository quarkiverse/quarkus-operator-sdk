package io.quarkiverse.operatorsdk.deployment;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Set;

import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.AnnotationConfigurable;
import io.javaoperatorsdk.operator.api.config.ControllerConfigurationBuilder;
import io.javaoperatorsdk.operator.api.config.ResolvedControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.Utils;
import io.javaoperatorsdk.operator.api.config.Utils.Configurator;
import io.javaoperatorsdk.operator.api.config.Utils.Instantiator;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import io.javaoperatorsdk.operator.processing.event.rate.LinearRateLimiter;
import io.quarkiverse.operatorsdk.common.AnnotationConfigurableAugmentedClassInfo;
import io.quarkiverse.operatorsdk.common.ClassLoadingUtils;
import io.quarkiverse.operatorsdk.common.ReconciledAugmentedClassInfo;
import io.quarkiverse.operatorsdk.common.ReconcilerAugmentedClassInfo;
import io.quarkiverse.operatorsdk.runtime.BuildTimeOperatorConfiguration;
import io.quarkiverse.operatorsdk.runtime.QuarkusControllerConfiguration;
import io.quarkiverse.operatorsdk.runtime.QuarkusControllerConfiguration.DefaultRateLimiter;
import io.quarkiverse.operatorsdk.runtime.QuarkusDependentResourceSpec;
import io.quarkiverse.operatorsdk.runtime.QuarkusKubernetesDependentResourceConfig;

@SuppressWarnings("rawtypes")
class QuarkusControllerConfigurationBuilder {

    static final Logger log = Logger.getLogger(QuarkusControllerConfigurationBuilder.class.getName());

    @SuppressWarnings("unchecked")
    QuarkusControllerConfiguration build(
            ReconcilerAugmentedClassInfo reconcilerInfo,
            Map<String, AnnotationConfigurableAugmentedClassInfo> configurableInfos,
            BuildTimeOperatorConfiguration buildTimeConfiguration, IndexView index) {

        final var reconcilerClassName = reconcilerInfo.classInfo().toString();
        final String name = reconcilerInfo.nameOrFailIfUnset();
        final var controllerConfiguration = new ControllerConfigurationAnnotation(
                reconcilerInfo, buildTimeConfiguration, index);
        final ReconciledAugmentedClassInfo primaryInfo = reconcilerInfo.associatedResourceInfo();
        final var resourceClass = primaryInfo.loadAssociatedClass();

        final var builder = new InternalQuarkusControllerConfigurationBuilder(reconcilerInfo);
        final ResolvedControllerConfiguration<? extends HasMetadata> resolved = builder
                .createConfiguration(controllerConfiguration, resourceClass, name, reconcilerClassName);

        final var primaryAsResource = primaryInfo.asResourceTargeting();
        final var resourceFullName = resolved.getResourceTypeName();
        final var configuration = new QuarkusControllerConfiguration(
                resourceClass, resolved,
                primaryAsResource.version(),
                primaryAsResource.hasNonVoidStatus(),
                getConfigurationClass(reconcilerInfo, resolved.getRetry(), configurableInfos),
                getConfigurationClass(reconcilerInfo, resolved.getRateLimiter(), configurableInfos));
        configuration.setEventFilter(resolved.getEventFilter());

        log.infov(
                "Processed ''{0}'' reconciler named ''{1}'' for ''{2}'' resource (version ''{3}'')",
                reconcilerClassName, name, resourceFullName, HasMetadata.getApiVersion(resourceClass));
        return configuration;
    }

    private static Class<?> getConfigurationClass(ReconcilerAugmentedClassInfo reconcilerInfo,
            Object toConfigure,
            Map<String, AnnotationConfigurableAugmentedClassInfo> configurableInfos) {
        final var configurableInfo = configurableInfos.get(toConfigure.getClass().getName());
        if (configurableInfo != null) {
            final var associatedConfigurationClass = configurableInfo.getAssociatedConfigurationClass();
            if (reconcilerInfo.classInfo().annotationsMap().containsKey(associatedConfigurationClass)) {
                return ClassLoadingUtils
                        .loadClass(associatedConfigurationClass.toString(), Object.class);
            }
        }
        return null;
    }

    private static class InternalQuarkusControllerConfigurationBuilder extends
            ControllerConfigurationBuilder {
        private final ReconcilerAugmentedClassInfo reconcilerInfo;

        public InternalQuarkusControllerConfigurationBuilder(ReconcilerAugmentedClassInfo reconcilerInfo) {
            super(SubstitutionInstantiator.instance);
            this.reconcilerInfo = reconcilerInfo;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected <T> Configurator<T> configuratorFor(Class<T> typeOfObjectToConfigure) {
            final Class<? extends Reconciler> reconcilerClass = ClassLoadingUtils
                    .loadClass(reconcilerInfo.classInfo().name().toString(), Reconciler.class);
            return instance -> {
                if (instance instanceof AnnotationConfigurable
                        && typeOfObjectToConfigure.isInstance(instance)) {
                    // todo: see if we can't resolve that information in the reconciler info at build time
                    final Class<? extends Annotation> configurationClass = (Class<? extends Annotation>) Utils
                            .getFirstTypeArgumentFromSuperClassOrInterface(
                                    instance.getClass(), AnnotationConfigurable.class);
                    final var configAnnotation = reconcilerClass.getAnnotation(configurationClass);

                    AnnotationConfigurable configurable = (AnnotationConfigurable) instance;
                    if (configAnnotation != null) {
                        configurable.initFrom(configAnnotation);
                    }
                }
            };
        }

        @Override
        @SuppressWarnings("unchecked")
        protected DependentResourceSpec newDependentSpec(DependentResource dependentResource,
                String dependentName, Set<String> dependsOn, Condition readyPostCondition,
                Condition reconcilePreCondition, Condition deletePostCondition,
                String eventSourceName) {
            return new QuarkusDependentResourceSpec(dependentResource, dependentName, dependsOn,
                    readyPostCondition, reconcilePreCondition, deletePostCondition, eventSourceName);
        }
    }

    private static class SubstitutionInstantiator implements Instantiator {
        private final static SubstitutionInstantiator instance = new SubstitutionInstantiator();

        private final static Map<String, Class<?>> substitutions = Map.of(
                LinearRateLimiter.class.getName(), DefaultRateLimiter.class,
                KubernetesDependentResourceConfig.class.getName(), QuarkusKubernetesDependentResourceConfig.class,
                KubernetesDependentResource.class.getName(), QuarkusKubernetesDependentResource.class);

        @Override
        @SuppressWarnings("unchecked")
        public <T> T instantiate(Class<T> aClass) {
            // substitute with Quarkus-friendly classes
            final Class<?> substitionClass = substitutions.get(aClass.getName());
            if (substitionClass != null) {
                aClass = (Class<T>) substitionClass;
            }
            return Instantiator.super.instantiate(aClass);
        }
    }
}
