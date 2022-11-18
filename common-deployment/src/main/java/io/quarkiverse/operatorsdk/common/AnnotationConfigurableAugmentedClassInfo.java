package io.quarkiverse.operatorsdk.common;

import java.util.Map;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

public class AnnotationConfigurableAugmentedClassInfo extends SelectiveAugmentedClassInfo {
    private DotName associatedConfigurationClass;

    protected AnnotationConfigurableAugmentedClassInfo(ClassInfo classInfo, DotName configurationInterface,
            int expectedParameterTypesCardinality) {
        super(classInfo, configurationInterface, expectedParameterTypesCardinality);
    }

    public AnnotationConfigurableAugmentedClassInfo(ClassInfo classInfo) {
        this(classInfo, Constants.ANNOTATION_CONFIGURABLE, 1);
    }

    @Override
    protected void doAugment(IndexView index, Logger log, Map<String, Object> context) {
        // record associated configuration class
        associatedConfigurationClass = typeAt(0).name();
    }

    public DotName getAssociatedConfigurationClass() {
        return associatedConfigurationClass;
    }

    @Override
    public String toString() {
        return classInfo().name().toString() + " -> " + associatedConfigurationClass.toString();
    }
}
