package io.quarkiverse.operatorsdk.common;

import static io.quarkiverse.operatorsdk.common.Constants.CONTROLLER_CONFIGURATION;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;

import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.Utils;

public class ConfigurationUtils {

    private ConfigurationUtils() {
    }

    /**
     * Extracts the appropriate configuration value for the controller checking first any annotation
     * configuration, then potentially overriding it by a properties-provided value or returning a
     * default value if neither is provided.
     *
     * @param <T> the expected type of the configuration value we're trying to extract
     * @param <C> the type of the external configuration values can be overridden from
     * @param annotation the annotation from which a field is to be extracted
     * @param extractor a Function extracting the optional value we're interested in from the
     *        external configuration
     * @param annotationField the name of the field we want to retrieve from the specified annotation
     *        if present
     * @param converter a Function converting the annotation value to the type we're expecting
     * @param defaultValue a Supplier that computes/retrieve a default value when needed
     * @return the extracted configuration value
     */
    public static <C, T> T extract(
            C externalConfig,
            AnnotationInstance annotation,
            Function<C, Optional<T>> extractor,
            String annotationField,
            Function<AnnotationValue, T> converter,
            Supplier<T> defaultValue) {
        // first check if we have an external configuration
        if (externalConfig != null) {
            // extract value from config if present
            return extractor
                    .apply(externalConfig)
                    // or get from the annotation or default
                    .orElse(
                            annotationValueOrDefault(annotation, annotationField, converter, defaultValue));
        } else {
            // get from annotation or default
            return annotationValueOrDefault(annotation, annotationField, converter, defaultValue);
        }
    }

    public static <T> T annotationValueOrDefault(
            AnnotationInstance annotation,
            String annotationFieldName,
            Function<AnnotationValue, T> converter,
            Supplier<T> defaultValue) {
        return annotation != null
                ?
                // get converted annotation value of get default
                Optional.ofNullable(annotation.value(annotationFieldName)).map(converter).orElseGet(defaultValue)
                :
                // get default
                defaultValue.get();
    }

    /**
     * Checks whether CRD presence should be checked on the cluster and if custom resources should be (somewhat) validated.
     * If the {@link Utils#CHECK_CRD_ENV_KEY} system property is set, this will be used, regardless of other configuration
     * options.
     * 
     * @param validate value from the build time configuration
     * @return the value specified by {@link Utils#CHECK_CRD_ENV_KEY} if set, the value specified by the build time
     *         configuration property otherwise
     */
    public static boolean shouldValidateCustomResources(boolean validate) {
        if (Utils.isValidateCustomResourcesEnvVarSet()) {
            return Utils.shouldCheckCRDAndValidateLocalModel();
        }
        return validate;
    }

    public static String getReconcilerName(ClassInfo info) {
        final var controllerClassName = info.name().toString();
        final var controllerAnnotation = info.classAnnotation(CONTROLLER_CONFIGURATION);
        return getReconcilerName(controllerClassName, controllerAnnotation);
    }

    public static String getReconcilerName(String reconcilerClassName, AnnotationInstance configuration) {
        final var defaultControllerName = ReconcilerUtils.getDefaultReconcilerName(reconcilerClassName);
        return ConfigurationUtils.annotationValueOrDefault(
                configuration, "name", AnnotationValue::asString, () -> defaultControllerName);
    }
}
