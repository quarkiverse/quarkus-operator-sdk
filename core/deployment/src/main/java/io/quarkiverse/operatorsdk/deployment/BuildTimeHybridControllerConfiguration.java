package io.quarkiverse.operatorsdk.deployment;

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
                controllerAnnotation, c -> c.generationAware,
                "generationAwareEventProcessing",
                AnnotationValue::asBoolean,
                () -> operatorConfiguration.generationAware.orElse(true));
    }
}
