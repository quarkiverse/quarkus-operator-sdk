package io.quarkiverse.operatorsdk.common;

import java.util.Map;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

public class AnnotatableDependentResourceAugmentedClassInfo extends AnnotationConfigurableAugmentedClassInfo {

    public AnnotatableDependentResourceAugmentedClassInfo(ClassInfo classInfo) {
        super(classInfo, Constants.ANNOTATION_DR_CONFIGURATOR, 2);
    }

    @Override
    protected void doAugment(IndexView index, Logger log, Map<String, Object> context) {
        super.doAugment(index, log, context);

        // register the configuration class itself for reflection
        registerForReflection(typeAt(1).name().toString());
    }
}
