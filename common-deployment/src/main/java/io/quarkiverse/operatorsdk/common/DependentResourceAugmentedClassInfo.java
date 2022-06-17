package io.quarkiverse.operatorsdk.common;

import static io.quarkiverse.operatorsdk.common.Constants.DEPENDENT_RESOURCE;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

public class DependentResourceAugmentedClassInfo extends SelectiveAugmentedClassInfo {

    public DependentResourceAugmentedClassInfo(ClassInfo classInfo) {
        super(classInfo, DEPENDENT_RESOURCE, 2);
    }

    @Override
    protected boolean augmentIfKept(IndexView index, Logger log) {
        // only need to check the secondary resource type since the primary should have already been processed with the associated reconciler
        final var secondaryTypeDN = typeAt(0).name();
        registerForReflection(secondaryTypeDN.toString());

        // check if the secondary resource is a CR (rare but possible)
        handlePossibleCR(secondaryTypeDN, index, log);

        return true;
    }
}
