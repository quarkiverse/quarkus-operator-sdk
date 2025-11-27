package io.quarkiverse.operatorsdk.runtime;

import java.util.Set;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.cache.ItemStore;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnDeleteFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class QuarkusInformerConfiguration<R extends HasMetadata> extends InformerConfiguration<R> {

    @RecordableConstructor
    public QuarkusInformerConfiguration(Class<R> resourceClass, String name, Set<String> namespaces,
            boolean followControllerNamespaceChanges, String labelSelector, OnAddFilter<? super R> onAddFilter,
            OnUpdateFilter<? super R> onUpdateFilter, OnDeleteFilter<? super R> onDeleteFilter,
            GenericFilter<? super R> genericFilter, ItemStore<R> itemStore, Long informerListLimit,
            QuarkusFieldSelector fieldSelector) {
        super(resourceClass, name, namespaces, followControllerNamespaceChanges, labelSelector, onAddFilter, onUpdateFilter,
                onDeleteFilter, genericFilter, itemStore, informerListLimit, fieldSelector);
    }

    public QuarkusInformerConfiguration(InformerConfiguration<R> config) {
        this(config.getResourceClass(),
                config.getName(),
                sanitizeNamespaces(config.getNamespaces()),
                config.getFollowControllerNamespaceChanges(),
                config.getLabelSelector(),
                config.getOnAddFilter(),
                config.getOnUpdateFilter(),
                config.getOnDeleteFilter(),
                config.getGenericFilter(),
                config.getItemStore(),
                config.getInformerListLimit(),
                config.getFieldSelector() == null ? null : new QuarkusFieldSelector(config.getFieldSelector()));
    }

    private static Set<String> sanitizeNamespaces(Set<String> namespaces) {
        return namespaces.stream().map(String::trim).collect(Collectors.toSet());
    }
}
