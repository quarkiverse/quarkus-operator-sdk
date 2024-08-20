package io.quarkiverse.operatorsdk.deployment;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;

import io.quarkiverse.operatorsdk.common.ConfigurationUtils;
import io.quarkiverse.operatorsdk.runtime.BuildTimeControllerConfiguration;
import io.quarkiverse.operatorsdk.runtime.BuildTimeOperatorConfiguration;

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
                controllerAnnotation, BuildTimeControllerConfiguration::generationAware,
                "generationAwareEventProcessing",
                AnnotationValue::asBoolean,
                () -> operatorConfiguration.generationAware().orElse(true));
    }

    Set<String> generateWithWatchedNamespaces(boolean wereNamespacesSet) {
        Set<String> namespaces = null;
        if (externalConfiguration != null) {
            Optional<List<String>> overrideNamespaces = externalConfiguration.generateWithWatchedNamespaces();
            if (overrideNamespaces.isPresent()) {
                namespaces = new HashSet<>(overrideNamespaces.get());
            }
        }

        // check if we have an operator-level configuration only if namespaces haven't been explicitly set already
        final var watchedNamespaces = operatorConfiguration.generateWithWatchedNamespaces();
        if (!wereNamespacesSet && namespaces == null && watchedNamespaces.isPresent()) {
            namespaces = new HashSet<>(watchedNamespaces.get());
        }

        return namespaces;
    }
}
