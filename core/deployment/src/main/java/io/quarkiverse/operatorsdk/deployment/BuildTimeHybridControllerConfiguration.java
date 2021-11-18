package io.quarkiverse.operatorsdk.deployment;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Type;
import org.jboss.jandex.Type.Kind;

import io.javaoperatorsdk.operator.ControllerUtils;
import io.quarkiverse.operatorsdk.common.ConfigurationUtils;
import io.quarkiverse.operatorsdk.runtime.BuildTimeControllerConfiguration;
import io.quarkiverse.operatorsdk.runtime.BuildTimeOperatorConfiguration;

class BuildTimeHybridControllerConfiguration {

    private final BuildTimeOperatorConfiguration operatorConfiguration;
    private final BuildTimeControllerConfiguration externalConfiguration;
    private final AnnotationInstance controllerAnnotation;
    private final AnnotationInstance delayRegistrationAnnotation;

    public BuildTimeHybridControllerConfiguration(
            BuildTimeOperatorConfiguration operatorConfiguration, BuildTimeControllerConfiguration externalConfiguration,
            AnnotationInstance controllerAnnotation,
            AnnotationInstance delayRegistrationAnnotation) {
        this.operatorConfiguration = operatorConfiguration;
        this.externalConfiguration = externalConfiguration;
        this.controllerAnnotation = controllerAnnotation;
        this.delayRegistrationAnnotation = delayRegistrationAnnotation;
    }

    boolean generationAware() {
        return ConfigurationUtils.extract(
                externalConfiguration,
                controllerAnnotation, c -> c.generationAware,
                "generationAwareEventProcessing",
                AnnotationValue::asBoolean,
                () -> operatorConfiguration.generationAware.orElse(true));
    }

    Type eventType() {
        return ConfigurationUtils.extract(
                externalConfiguration,
                delayRegistrationAnnotation, c -> fromName(c.delayRegistrationUntilEvent),
                "event",
                AnnotationValue::asClass,
                () -> fromName(operatorConfiguration.delayRegistrationUntilEvent).orElse(null));
    }

    private Optional<Type> fromName(Optional<String> className) {
        return className.filter(s -> void.class.getName().equals(s))
                .map(DotName::createSimple)
                .map(dn -> Type.create(dn, Kind.CLASS));
    }

    boolean delayedRegistration() {
        return ConfigurationUtils.extract(
                externalConfiguration,
                delayRegistrationAnnotation,
                c -> hasNonVoidRegistrationEvent(c.delayRegistrationUntilEvent),
                "event",
                v -> v.asClass().kind() != Kind.VOID,
                () -> hasNonVoidRegistrationEvent(operatorConfiguration.delayRegistrationUntilEvent).orElse(false));
    }

    private Optional<Boolean> hasNonVoidRegistrationEvent(Optional<String> className) {
        return className.map(s -> void.class.getName().equals(s));
    }

    String name(String resourceControllerClassName) {
        // retrieve the controller's name
        final var defaultControllerName = ControllerUtils
                .getDefaultResourceControllerName(resourceControllerClassName);
        return ConfigurationUtils.annotationValueOrDefault(
                controllerAnnotation, "name", AnnotationValue::asString, () -> defaultControllerName);
    }

    List<String> namespaces() {
        final var namespaces = ConfigurationUtils.extract(
                externalConfiguration,
                controllerAnnotation,
                c -> c.namespaces,
                "namespaces",
                v -> Arrays.asList(v.asStringArray()),
                Collections::emptyList);
        return namespaces;
    }
}
