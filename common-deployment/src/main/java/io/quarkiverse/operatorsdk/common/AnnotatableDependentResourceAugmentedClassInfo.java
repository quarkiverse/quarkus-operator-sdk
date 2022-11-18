package io.quarkiverse.operatorsdk.common;

import org.jboss.jandex.ClassInfo;

public class AnnotatableDependentResourceAugmentedClassInfo extends AnnotationConfigurableAugmentedClassInfo {

    public AnnotatableDependentResourceAugmentedClassInfo(ClassInfo classInfo) {
        super(classInfo, Constants.ANNOTATION_DR_CONFIGURATOR, 2);
    }
}
