package io.quarkiverse.operatorsdk.deployment;

import static io.quarkiverse.operatorsdk.common.Constants.CONTROLLER_CONFIGURATION;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Type;

import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.MaxReconciliationInterval;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.rate.LinearRateLimiter;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimiter;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.processing.retry.Retry;
import io.quarkiverse.operatorsdk.common.ClassLoadingUtils;
import io.quarkiverse.operatorsdk.common.ConfigurationUtils;
import io.quarkiverse.operatorsdk.common.ReconcilerAugmentedClassInfo;
import io.quarkiverse.operatorsdk.runtime.BuildTimeOperatorConfiguration;

public class ControllerConfigurationAnnotation implements ControllerConfiguration {
    private final ReconcilerAugmentedClassInfo reconcilerInfo;
    private final AnnotationInstance controllerAnnotation;
    private final BuildTimeHybridControllerConfiguration configExtractor;
    private final String name;
    private final IndexView index;

    public ControllerConfigurationAnnotation(ReconcilerAugmentedClassInfo reconcilerInfo,
            BuildTimeOperatorConfiguration buildTimeConfiguration, IndexView index) {
        this.reconcilerInfo = reconcilerInfo;
        name = reconcilerInfo.nameOrFailIfUnset();

        // extract the configuration from annotation and/or external configuration
        controllerAnnotation = reconcilerInfo.classInfo().declaredAnnotation(CONTROLLER_CONFIGURATION);
        configExtractor = new BuildTimeHybridControllerConfiguration(buildTimeConfiguration,
                buildTimeConfiguration.controllers.get(name),
                controllerAnnotation);

        this.index = index;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String finalizerName() {
        return ConfigurationUtils.annotationValueOrDefault(controllerAnnotation,
                "finalizerName",
                AnnotationValue::asString,
                () -> Constants.NO_VALUE_SET);
    }

    @Override
    public boolean generationAwareEventProcessing() {
        return configExtractor.generationAware();
    }

    @Override
    public String[] namespaces() {
        return configExtractor.namespaces(name);
    }

    @Override
    public String labelSelector() {
        return getLabelSelector(controllerAnnotation);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<? extends ResourceEventFilter>[] eventFilters() {
        return Arrays.stream(ConfigurationUtils.annotationValueOrDefault(
                controllerAnnotation, "eventFilters",
                AnnotationValue::asClassArray, () -> new Type[0]))
                .map(t -> ClassLoadingUtils.loadClass(t.name().toString(), ResourceEventFilter.class))
                .toArray(Class[]::new);
    }

    @Override
    public Class<? extends OnAddFilter> onAddFilter() {
        return loadClass(controllerAnnotation, index, "onAddFilter", OnAddFilter.class, OnAddFilter.class);
    }

    static <T> Class<? extends T> loadClass(AnnotationInstance annotationInstance, IndexView index,
            String annotationFieldName, Class<T> interfaceClass, Class<? extends T> defaultClass) {
        return annotationInstance != null
                ?
                // get converted annotation value of get default
                Optional.ofNullable(annotationInstance.value(annotationFieldName))
                        .map(av -> {
                            final var expectedTypeInfo = ConfigurationUtils.getClassInfoForInstantiation(av,
                                    interfaceClass,
                                    index);

                            final var typeName = expectedTypeInfo.name().toString();
                            return ClassLoadingUtils.loadClass(typeName, interfaceClass);
                        })
                        .orElse((Class<T>) defaultClass)
                :
                // get default
                defaultClass;
    }

    @Override
    public Class<? extends OnUpdateFilter> onUpdateFilter() {
        return loadClass(controllerAnnotation, index, "onUpdateFilter",
                OnUpdateFilter.class, OnUpdateFilter.class);
    }

    @Override
    public Class<? extends GenericFilter> genericFilter() {
        return loadClass(controllerAnnotation, index, "genericFilter", GenericFilter.class, GenericFilter.class);
    }

    @Override
    public MaxReconciliationInterval maxReconciliationInterval() {
        final var intervalFromAnnotation = ConfigurationUtils.annotationValueOrDefault(
                controllerAnnotation, "maxReconciliationInterval", AnnotationValue::asNested,
                () -> null);
        return new MaxReconciliationInterval() {

            @Override
            public Class<? extends Annotation> annotationType() {
                return MaxReconciliationInterval.class;
            }

            @Override
            public long interval() {
                return ConfigurationUtils.annotationValueOrDefault(
                        intervalFromAnnotation, "interval", AnnotationValue::asLong,
                        () -> MaxReconciliationInterval.DEFAULT_INTERVAL);
            }

            @Override
            public TimeUnit timeUnit() {
                return ConfigurationUtils.annotationValueOrDefault(
                        intervalFromAnnotation,
                        "timeUnit",
                        av -> TimeUnit.valueOf(av.asEnum()),
                        () -> TimeUnit.HOURS);
            }
        };
    }

    @Override
    public Dependent[] dependents() {
        final var dependentResourceInfos = reconcilerInfo.getDependentResourceInfos();
        if (!dependentResourceInfos.isEmpty()) {
            return dependentResourceInfos.stream()
                    .map(draci -> new DependentAnnotation(draci, index))
                    .toArray(Dependent[]::new);
        } else {
            return new Dependent[0];
        }
    }

    @Override
    public Class<? extends Retry> retry() {
        return loadClass(controllerAnnotation, index, "retry", Retry.class, GenericRetry.class);
    }

    @Override
    public Class<? extends RateLimiter> rateLimiter() {
        return loadClass(controllerAnnotation, index, "rateLimiter", RateLimiter.class, LinearRateLimiter.class);
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return ControllerConfiguration.class;
    }

    private String getLabelSelector(AnnotationInstance annotation) {
        return ConfigurationUtils.annotationValueOrDefault(annotation,
                "labelSelector",
                AnnotationValue::asString,
                () -> null);
    }
}
