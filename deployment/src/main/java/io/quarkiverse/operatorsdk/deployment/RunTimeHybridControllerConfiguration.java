package io.quarkiverse.operatorsdk.deployment;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;

import io.javaoperatorsdk.operator.ControllerUtils;
import io.javaoperatorsdk.operator.api.config.RetryConfiguration;
import io.quarkiverse.operatorsdk.runtime.ExternalControllerConfiguration;

class RunTimeHybridControllerConfiguration {
    private final ValueExtractor<ExternalControllerConfiguration> extractor;
    private final AnnotationInstance controllerAnnotation;

    public RunTimeHybridControllerConfiguration(ExternalControllerConfiguration externalConfiguration,
            AnnotationInstance controllerAnnotation) {
        this.extractor = new ValueExtractor<>(externalConfiguration);
        this.controllerAnnotation = controllerAnnotation;
    }

    String finalizer(final String crdName) {
        return extractor.extract(
                controllerAnnotation, c -> c.finalizer,
                "finalizerName",
                AnnotationValue::asString,
                () -> ControllerUtils.getDefaultFinalizerName(crdName));
    }

    String[] namespaces() {
        return extractor.extract(
                controllerAnnotation, c -> c.namespaces.map(l -> l.toArray(new String[0])),
                "namespaces",
                AnnotationValue::asStringArray,
                () -> new String[] {});
    }

    RetryConfiguration retryConfiguration() {
        final var runtimeConf = extractor.getConfiguration();
        return runtimeConf == null ? null : RetryConfigurationResolver.resolve(runtimeConf.retry);
    }

}
