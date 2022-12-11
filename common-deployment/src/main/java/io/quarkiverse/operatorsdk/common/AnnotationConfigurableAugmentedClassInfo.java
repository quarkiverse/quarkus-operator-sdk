package io.quarkiverse.operatorsdk.common;

import java.util.Map;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

public class AnnotationConfigurableAugmentedClassInfo extends SelectiveAugmentedClassInfo {
    private DotName associatedConfigurationClass;

    public AnnotationConfigurableAugmentedClassInfo(ClassInfo classInfo) {
        super(classInfo, Constants.ANNOTATION_CONFIGURABLE, 1);
    }

    @Override
    protected void doAugment(IndexView index, Logger log, Map<String, Object> context) {
        // record associated configuration annotation
        associatedConfigurationClass = typeAt(0).name();

        // register associated class for reflection: this class is the annotation target class
        registerForReflection(classInfo().name().toString());
    }

    public DotName getAssociatedConfigurationClass() {
        return associatedConfigurationClass;
    }

    @Override
    public String toString() {
        return classInfo().name().toString() + " -> " + associatedConfigurationClass.toString();
    }
}
