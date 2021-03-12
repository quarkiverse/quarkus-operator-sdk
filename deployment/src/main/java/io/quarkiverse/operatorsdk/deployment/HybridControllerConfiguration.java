package io.quarkiverse.operatorsdk.deployment;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Type;
import org.jboss.jandex.Type.Kind;

import io.javaoperatorsdk.operator.ControllerUtils;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.config.RetryConfiguration;
import io.quarkiverse.operatorsdk.runtime.DelayRegistrationUntil;
import io.quarkiverse.operatorsdk.runtime.ExternalControllerConfiguration;
import io.quarkiverse.operatorsdk.runtime.OperatorRunTimeConfiguration;

/**
 * Encapsulates controller configuration values that might come from either annotation or external
 * properties file.
 */
class HybridControllerConfiguration {

    private final ValueExtractor extractor;
    private final String name;
    private final ExternalControllerConfiguration extConfig;
    private final AnnotationInstance controllerAnnotation;
    private final AnnotationInstance delayRegistrationAnnotation;

    /**
     * Creates a new HybridControllerConfiguration
     *
     * @param resourceControllerClassName the fully-qualified name of the associated {@link
     *        ResourceController} class
     * @param externalConfiguration the external configuration
     * @param info the {@link ClassInfo} from which information needs to be
     *        extracted
     */
    public HybridControllerConfiguration(
            String resourceControllerClassName,
            OperatorRunTimeConfiguration externalConfiguration,
            ClassInfo info) {
        this.controllerAnnotation = info.classAnnotation(
                DotName.createSimple(Controller.class.getName()));
        this.delayRegistrationAnnotation = info.classAnnotation(
                DotName.createSimple(DelayRegistrationUntil.class.getName()));
        // retrieve the controller's name
        final var defaultControllerName = ControllerUtils
                .getDefaultResourceControllerName(resourceControllerClassName);
        this.name = ValueExtractor.annotationValueOrDefault(
                controllerAnnotation, "name", AnnotationValue::asString, () -> defaultControllerName);

        this.extConfig = externalConfiguration.controllers.get(name);
        this.extractor = new ValueExtractor(extConfig);
    }

    String name() {
        return name;
    }

    String finalizer(final String crdName) {
        return extractor.extract(
                controllerAnnotation, c -> c.finalizer,
                "finalizerName",
                AnnotationValue::asString,
                () -> ControllerUtils.getDefaultFinalizerName(crdName));
    }

    boolean generationAware() {
        return extractor.extract(
                controllerAnnotation, c -> c.generationAware,
                "generationAwareEventProcessing",
                AnnotationValue::asBoolean,
                () -> true);
    }

    String[] namespaces() {
        return extractor.extract(
                controllerAnnotation, c -> c.namespaces.map(l -> l.toArray(new String[0])),
                "namespaces",
                AnnotationValue::asStringArray,
                () -> new String[] {});
    }

    RetryConfiguration retryConfiguration() {
        return extConfig == null ? null : RetryConfigurationResolver.resolve(extConfig.retry);
    }

    Type eventType() {
        return extractor.extract(
                delayRegistrationAnnotation, c -> c.delayRegistrationUntilEvent
                        .filter(s -> void.class.getName().equals(s))
                        .map(DotName::createSimple)
                        .map(dn -> Type.create(dn, Kind.CLASS)),
                "event",
                AnnotationValue::asClass,
                () -> null);
    }

    boolean delayedRegistration() {
        return extractor.extract(
                delayRegistrationAnnotation,
                c -> c.delayRegistrationUntilEvent.map(s -> void.class.getName().equals(s)),
                "event",
                v -> v.asClass().kind() != Kind.VOID,
                () -> false);
    }
}
