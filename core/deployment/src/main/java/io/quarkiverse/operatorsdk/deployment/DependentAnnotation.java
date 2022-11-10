package io.quarkiverse.operatorsdk.deployment;

import java.lang.annotation.Annotation;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.IndexView;

import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import io.quarkiverse.operatorsdk.common.ConfigurationUtils;
import io.quarkiverse.operatorsdk.common.DependentResourceAugmentedClassInfo;

public class DependentAnnotation implements Dependent {
    private final DependentResourceAugmentedClassInfo dependent;
    private final AnnotationInstance annotation;
    private final IndexView index;

    public DependentAnnotation(DependentResourceAugmentedClassInfo dependent, IndexView index) {
        this.dependent = dependent;
        annotation = dependent.getDependentAnnotationFromController();

        this.index = index;
    }

    @Override
    public Class<? extends DependentResource> type() {
        return ControllerConfigurationAnnotation.loadClass(annotation, index, "type", DependentResource.class, null);
    }

    @Override
    public String name() {
        return ConfigurationUtils.annotationValueOrDefault(annotation, "name", AnnotationValue::asString,
                () -> Constants.NO_VALUE_SET);
    }

    @Override
    public Class<? extends Condition> readyPostcondition() {
        return ControllerConfigurationAnnotation.loadClass(annotation, index, "readyPostcondition",
                Condition.class, Condition.class);
    }

    @Override
    public Class<? extends Condition> reconcilePrecondition() {
        return ControllerConfigurationAnnotation.loadClass(annotation, index, "reconcilePrecondition",
                Condition.class, Condition.class);
    }

    @Override
    public Class<? extends Condition> deletePostcondition() {
        return ControllerConfigurationAnnotation.loadClass(annotation, index, "deletePostcondition",
                Condition.class, Condition.class);
    }

    @Override
    public String[] dependsOn() {
        return ConfigurationUtils.annotationValueOrDefault(annotation, "dependsOn", AnnotationValue::asStringArray,
                () -> new String[0]);
    }

    @Override
    public String useEventSourceWithName() {
        return ConfigurationUtils.annotationValueOrDefault(annotation, "useEventSourceWithName",
                AnnotationValue::asString,
                () -> Constants.NO_VALUE_SET);
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return Dependent.class;
    }
}
