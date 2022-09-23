package io.quarkiverse.operatorsdk.common;

import java.util.Map;
import java.util.Optional;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

public class ResourceAssociatedAugmentedClassInfo extends SelectiveAugmentedClassInfo {
    private final String name;
    private ReconciledAugmentedClassInfo<?> resourceInfo;

    protected ResourceAssociatedAugmentedClassInfo(ClassInfo classInfo,
            DotName extendedOrImplementedClass, int expectedParameterTypesCardinality, String name) {
        super(classInfo, extendedOrImplementedClass, expectedParameterTypesCardinality);
        this.name = name;
    }

    public DotName resourceTypeName() {
        return typeAt(0).name();
    }

    public String nameOrFailIfUnset() {
        return name().orElseThrow();
    }

    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    @Override
    protected void doAugment(IndexView index, Logger log, Map<String, Object> context) {
        // create associated resource information
        final var primaryTypeDN = resourceTypeName();
        final var primaryCI = index.getClassByName(primaryTypeDN);
        if (primaryCI == null) {
            throw new IllegalStateException("'" + primaryTypeDN
                    + "' has not been found in the Jandex index so it cannot be introspected. Please index your classes with Jandex.");
        }

        resourceInfo = ReconciledAugmentedClassInfo.createFor(primaryCI, name, index, log, context);
    }

    public ReconciledAugmentedClassInfo<?> associatedResourceInfo() {
        return resourceInfo;
    }
}
