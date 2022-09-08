package io.quarkiverse.operatorsdk.common;

import static io.quarkiverse.operatorsdk.common.Constants.CUSTOM_RESOURCE;
import static io.quarkiverse.operatorsdk.common.Constants.HAS_METADATA;
import static io.quarkiverse.operatorsdk.common.Constants.RECONCILER;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.HasMetadata;

/**
 * Metadata about a processable reconciler implementation.
 */
public class ReconcilerAugmentedClassInfo extends SelectiveAugmentedClassInfo implements LoadableResourceHolder<HasMetadata> {

    private final String name;
    private boolean isCR;
    private boolean hasNonVoidStatus;
    private SimpleLoadableResourceHolder<HasMetadata> holder;

    public ReconcilerAugmentedClassInfo(ClassInfo classInfo) {
        super(classInfo, RECONCILER, 1);
        this.name = ConfigurationUtils.getReconcilerName(classInfo);
    }

    public DotName primaryTypeName() {
        return typeAt(0).name();
    }

    public String name() {
        return name;
    }

    @Override
    protected boolean augmentIfKept(IndexView index, Logger log) {
        final var primaryTypeDN = primaryTypeName();

        // if we get CustomResource instead of a subclass, ignore the controller since we cannot do anything with it
        if (primaryTypeDN.toString() == null || CUSTOM_RESOURCE.equals(primaryTypeDN)
                || HAS_METADATA.equals(primaryTypeDN)) {
            log.warnv(
                    "Skipped processing of ''{0}'' {1} as it''s not parameterized with a CustomResource or HasMetadata sub-class",
                    name(), extendedOrImplementedClassName());
            return false;
        }

        // record target resource class for later forced registration for reflection
        registerForReflection(primaryTypeDN.toString());

        // check if the primary is also a CR, in which case we also need to register its
        // spec and status classes if we can determine them
        final var crStatus = handlePossibleCR(primaryTypeDN, index, log);
        isCR = crStatus.isCR;
        hasNonVoidStatus = crStatus.hasNonVoidStatus;

        final ClassInfo primaryCI = index.getClassByName(primaryTypeDN);
        holder = new SimpleLoadableResourceHolder<>(primaryCI);

        return true;
    }

    @Override
    public String getAssociatedResourceTypeName() {
        return holder.getAssociatedResourceTypeName();
    }

    @Override
    public Class<HasMetadata> loadAssociatedClass() {
        return holder.loadAssociatedClass();
    }

    public boolean isCRTargeting() {
        return isCR;
    }

    public boolean hasNonVoidStatus() {
        return hasNonVoidStatus;
    }
}
