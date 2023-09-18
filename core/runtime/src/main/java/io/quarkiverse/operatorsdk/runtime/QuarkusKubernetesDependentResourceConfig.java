package io.quarkiverse.operatorsdk.runtime;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnDeleteFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class QuarkusKubernetesDependentResourceConfig<R extends HasMetadata> extends KubernetesDependentResourceConfig<R> {

    @RecordableConstructor
    public QuarkusKubernetesDependentResourceConfig(Set<String> namespaces, String labelSelector,
            boolean configuredNS,
            boolean createResourceOnlyIfNotExistingWithSSA,
            ResourceDiscriminator<R, ?> resourceDiscriminator,
            Boolean useSSA,
            OnAddFilter<R> onAddFilter,
            OnUpdateFilter<R> onUpdateFilter,
            OnDeleteFilter<R> onDeleteFilter,
            GenericFilter<R> genericFilter) {
        super(namespaces, labelSelector, configuredNS, createResourceOnlyIfNotExistingWithSSA, resourceDiscriminator,
                useSSA, onAddFilter, onUpdateFilter, onDeleteFilter, genericFilter);
    }

    // Needed for the recordable constructor
    public Set<String> getNamespaces() {
        return namespaces();
    }

    public void setNamespaces(Set<String> namespaces) {
        super.setNamespaces(namespaces);
    }

    // Needed for the recordable constructor
    @SuppressWarnings("unused")
    public String getLabelSelector() {
        return labelSelector();
    }

    // Needed for the recordable constructor
    @SuppressWarnings({ "unchecked", "unused" })
    public OnAddFilter<R> getOnAddFilter() {
        return onAddFilter();
    }

    // Needed for the recordable constructor
    @SuppressWarnings({ "unused" })
    public OnUpdateFilter<R> getOnUpdateFilter() {
        return onUpdateFilter();
    }

    // Needed for the recordable constructor
    @SuppressWarnings({ "unused" })
    public OnDeleteFilter<R> getOnDeleteFilter() {
        return onDeleteFilter();
    }

    // Needed for the recordable constructor
    @SuppressWarnings("unused")
    public GenericFilter<R> getGenericFilter() {
        return genericFilter();
    }

    // Needed for the recordable constructor
    @SuppressWarnings("unused")
    public boolean getConfiguredNS() {
        return wereNamespacesConfigured();
    }

    // Needed for the recordable constructor
    @SuppressWarnings("unused")
    public boolean isCreateResourceOnlyIfNotExistingWithSSA() {
        return createResourceOnlyIfNotExistingWithSSA();
    }

    // Needed for the recordable constructor
    @SuppressWarnings("unused")
    public Boolean isUseSSA() {
        return useSSA().orElse(null);
    }
}
