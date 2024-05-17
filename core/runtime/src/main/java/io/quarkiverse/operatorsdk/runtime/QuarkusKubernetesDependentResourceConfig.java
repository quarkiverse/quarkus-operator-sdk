package io.quarkiverse.operatorsdk.runtime;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
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
            Boolean useSSA,
            OnAddFilter<R> onAddFilter,
            OnUpdateFilter<R> onUpdateFilter,
            OnDeleteFilter<R> onDeleteFilter,
            GenericFilter<R> genericFilter) {
        super(namespaces, labelSelector, configuredNS, createResourceOnlyIfNotExistingWithSSA,
                useSSA, onAddFilter, onUpdateFilter, onDeleteFilter, genericFilter);
    }

    // Getter required for Quarkus' RecordableConstructor, must match the associated constructor parameter name
    public Set<String> getNamespaces() {
        return namespaces();
    }

    public void setNamespaces(Set<String> namespaces) {
        super.setNamespaces(namespaces);
    }

    // Getter required for Quarkus' RecordableConstructor, must match the associated constructor parameter name
    @SuppressWarnings("unused")
    public String getLabelSelector() {
        return labelSelector();
    }

    // Getter required for Quarkus' RecordableConstructor, must match the associated constructor parameter name
    @SuppressWarnings({ "unchecked", "unused" })
    public OnAddFilter<R> getOnAddFilter() {
        return onAddFilter();
    }

    // Getter required for Quarkus' RecordableConstructor, must match the associated constructor parameter name
    @SuppressWarnings({ "unused" })
    public OnUpdateFilter<R> getOnUpdateFilter() {
        return onUpdateFilter();
    }

    // Getter required for Quarkus' RecordableConstructor, must match the associated constructor parameter name
    @SuppressWarnings({ "unused" })
    public OnDeleteFilter<R> getOnDeleteFilter() {
        return onDeleteFilter();
    }

    // Getter required for Quarkus' RecordableConstructor, must match the associated constructor parameter name
    @SuppressWarnings("unused")
    public GenericFilter<R> getGenericFilter() {
        return genericFilter();
    }

    // Getter required for Quarkus' RecordableConstructor, must match the associated constructor parameter name
    @SuppressWarnings("unused")
    public boolean getConfiguredNS() {
        return wereNamespacesConfigured();
    }

    // Getter required for Quarkus' RecordableConstructor, must match the associated constructor parameter name
    @SuppressWarnings("unused")
    public boolean isCreateResourceOnlyIfNotExistingWithSSA() {
        return createResourceOnlyIfNotExistingWithSSA();
    }

    // Getter required for Quarkus' RecordableConstructor, must match the associated constructor parameter name
    @SuppressWarnings("unused")
    public Boolean isUseSSA() {
        return useSSA().orElse(null);
    }
}
