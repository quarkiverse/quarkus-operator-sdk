package io.quarkiverse.operatorsdk.runtime;

import java.util.Set;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.cache.ItemStore;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.InformerConfigHolder;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnDeleteFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class QuarkusInformerConfigHolder<R extends HasMetadata> extends InformerConfigHolder<R> {

    @RecordableConstructor
    public QuarkusInformerConfigHolder(String name, Set<String> namespaces,
            String labelSelector, OnAddFilter<? super R> onAddFilter,
            OnUpdateFilter<? super R> onUpdateFilter, OnDeleteFilter<? super R> onDeleteFilter,
            GenericFilter<? super R> genericFilter, ItemStore<R> itemStore, Long informerListLimit) {
        super(name, namespaces, false, labelSelector, onAddFilter, onUpdateFilter, onDeleteFilter,
                genericFilter, itemStore, informerListLimit);
    }

    public QuarkusInformerConfigHolder(InformerConfigHolder<R> config) {
        this(config.getName(), sanitizeNamespaces(config.getNamespaces()),
                config.getLabelSelector(),
                config.getOnAddFilter(), config.getOnUpdateFilter(), config.getOnDeleteFilter(), config.getGenericFilter(),
                config.getItemStore(), config.getInformerListLimit());
    }

    private static Set<String> sanitizeNamespaces(Set<String> namespaces) {
        return namespaces.stream().map(String::trim).collect(Collectors.toSet());
    }
}
