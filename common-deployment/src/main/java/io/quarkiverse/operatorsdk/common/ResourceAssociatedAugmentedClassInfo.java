package io.quarkiverse.operatorsdk.common;

import static io.quarkiverse.operatorsdk.common.Constants.CUSTOM_RESOURCE;
import static io.quarkiverse.operatorsdk.common.Constants.HAS_METADATA;
import static io.quarkiverse.operatorsdk.common.Constants.OBJECT;

import java.util.Map;
import java.util.Optional;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

import io.quarkus.builder.BuildException;
import io.quarkus.deployment.util.JandexUtil;

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

    protected void augmentIfKept(IndexView index, Logger log, Map<String, Object> context) {
        // create associated resource information
        final var primaryTypeDN = resourceTypeName();
        final var primaryCI = index.getClassByName(primaryTypeDN);
        if (primaryCI == null) {
            throw new IllegalStateException("'" + primaryTypeDN
                    + "' has not been found in the Jandex index so it cannot be introspected. Please index your classes with Jandex.");
        }

        var isResource = false;
        var isCR = false;
        try {
            isResource = ClassUtils.isImplementationOf(index, primaryCI, HAS_METADATA);
            if (isResource) {
                isCR = JandexUtil.isSubclassOf(index, primaryCI, CUSTOM_RESOURCE);
            }
        } catch (BuildException e) {
            log.errorv(
                    "Couldn't ascertain if ''{0}'' is a CustomResource or HasMetadata subclass. Assumed not to be.",
                    e);
        }

        if (isCR) {
            resourceInfo = new CustomResourceAugmentedClassInfo(primaryCI, name);
        } else if (isResource) {
            resourceInfo = new ReconciledResourceAugmentedClassInfo<>(primaryCI, HAS_METADATA, 0, name);
        } else {
            resourceInfo = new ReconciledAugmentedClassInfo<>(primaryCI, OBJECT, 0, name);
        }
        // make sure the associated resource is properly initialized
        resourceInfo.augmentIfKept(index, log, context);
    }

    public ReconciledAugmentedClassInfo<?> associatedResourceInfo() {
        return resourceInfo;
    }
}
