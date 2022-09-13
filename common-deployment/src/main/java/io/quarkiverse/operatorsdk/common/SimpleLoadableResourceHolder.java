package io.quarkiverse.operatorsdk.common;

import static io.quarkiverse.operatorsdk.common.ClassLoadingUtils.loadClass;

import org.jboss.jandex.ClassInfo;

import io.fabric8.kubernetes.api.model.HasMetadata;

public class SimpleLoadableResourceHolder<T extends HasMetadata> implements LoadableResourceHolder<T> {

    private Class<T> clazz;
    private final String resourceTypeName;
    private final String resourceClassName;
    private final ClassInfo resourceCI;

    public SimpleLoadableResourceHolder(ClassInfo resourceCI) {
        this.resourceTypeName = HasMetadataUtils.getFullResourceName(resourceCI);
        this.resourceClassName = resourceCI.name().toString();
        this.resourceCI = resourceCI;
    }

    public String getAssociatedResourceTypeName() {
        return resourceTypeName;
    }

    @SuppressWarnings("unchecked")
    public Class<T> loadAssociatedClass() {
        if (clazz == null) {
            clazz = (Class<T>) loadClass(resourceClassName, HasMetadata.class);
        }
        return clazz;
    }

    public ClassInfo getResourceCI() {
        return resourceCI;
    }
}
