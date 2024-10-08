package io.quarkiverse.operatorsdk.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

public class ResourceAssociatedAugmentedClassInfo extends SelectiveAugmentedClassInfo {
    private final String name;
    private ReconciledAugmentedClassInfo<?> resourceInfo;
    private final String reconcilerName;

    protected ResourceAssociatedAugmentedClassInfo(ClassInfo classInfo,
            DotName extendedOrImplementedClass, int expectedParameterTypesCardinality, String name) {
        this(classInfo, extendedOrImplementedClass, expectedParameterTypesCardinality, name, null);
    }

    protected ResourceAssociatedAugmentedClassInfo(ClassInfo classInfo,
            DotName extendedOrImplementedClass, int expectedParameterTypesCardinality, String name, String reconcilerName) {
        super(classInfo, extendedOrImplementedClass, expectedParameterTypesCardinality);
        this.name = name;
        this.reconcilerName = reconcilerName != null ? reconcilerName : name;
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

        resourceInfo = ReconciledAugmentedClassInfo.createFor(this, primaryCI, reconcilerName, index, log, context);
    }

    public ReconciledAugmentedClassInfo<?> associatedResourceInfo() {
        return resourceInfo;
    }

    @Override
    public List<String> getClassNamesToRegisterForReflection() {
        final var own = super.getClassNamesToRegisterForReflection();
        final var associated = resourceInfo.getClassNamesToRegisterForReflection();
        final var result = new ArrayList<String>(own.size() + associated.size());
        result.addAll(own);
        result.addAll(associated);
        return result;
    }
}
