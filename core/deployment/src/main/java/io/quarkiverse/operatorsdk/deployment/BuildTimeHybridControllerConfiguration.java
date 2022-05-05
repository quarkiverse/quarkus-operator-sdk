package io.quarkiverse.operatorsdk.deployment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;

import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.ResourceConfiguration;
import io.quarkiverse.operatorsdk.common.ConfigurationUtils;
import io.quarkiverse.operatorsdk.runtime.BuildTimeControllerConfiguration;
import io.quarkiverse.operatorsdk.runtime.BuildTimeOperatorConfiguration;
import io.smallrye.config.Converters;
import io.smallrye.config.Expressions;

class BuildTimeHybridControllerConfiguration {

    private final BuildTimeOperatorConfiguration operatorConfiguration;
    private final BuildTimeControllerConfiguration externalConfiguration;
    private final AnnotationInstance controllerAnnotation;

    public BuildTimeHybridControllerConfiguration(
            BuildTimeOperatorConfiguration operatorConfiguration, BuildTimeControllerConfiguration externalConfiguration,
            AnnotationInstance controllerAnnotation) {
        this.operatorConfiguration = operatorConfiguration;
        this.externalConfiguration = externalConfiguration;
        this.controllerAnnotation = controllerAnnotation;
    }

    boolean generationAware() {
        return ConfigurationUtils.extract(
                externalConfiguration,
                controllerAnnotation, c -> c.generationAware,
                "generationAwareEventProcessing",
                AnnotationValue::asBoolean,
                () -> operatorConfiguration.generationAware.orElse(true));
    }

    String name(String resourceControllerClassName) {
        // retrieve the controller's name
        final var defaultControllerName = ReconcilerUtils.getDefaultReconcilerName(resourceControllerClassName);
        return ConfigurationUtils.annotationValueOrDefault(
                controllerAnnotation, "name", AnnotationValue::asString, () -> defaultControllerName);
    }

    Set<String> namespaces(String controllerName) {
        // first check if we have a property for the namespaces, retrieving it without expanding it
        final var config = ConfigProvider.getConfig();
        var withoutExpansion = Expressions.withoutExpansion(
                () -> config.getConfigValue("quarkus.operator-sdk.controllers."
                        + controllerName + ".namespaces").getRawValue());

        // check if the controller name is doubly quoted
        if (withoutExpansion == null) {
            withoutExpansion = Expressions.withoutExpansion(
                    () -> config.getConfigValue("quarkus.operator-sdk.controllers.\""
                            + controllerName + "\".namespaces").getRawValue());
        }

        // check if the controller name is simply quoted
        if (withoutExpansion == null) {
            withoutExpansion = Expressions.withoutExpansion(
                    () -> config.getConfigValue("quarkus.operator-sdk.controllers.'"
                            + controllerName + "'.namespaces").getRawValue());
        }

        if (withoutExpansion != null) {
            // if we have a property, use it and convert it to a set of namespaces,
            // potentially with unexpanded variable names as namespace names
            final var converter = Converters.newCollectionConverter(
                    Converters.getImplicitConverter(String.class), ArrayList::new);
            final var namespaces = converter.convert(withoutExpansion);
            return new HashSet<>(namespaces);
        }
        return ConfigurationUtils.annotationValueOrDefault(controllerAnnotation,
                "namespaces",
                v -> new HashSet<>(Arrays.asList(v.asStringArray())),
                () -> ResourceConfiguration.DEFAULT_NAMESPACES);
    }
}
