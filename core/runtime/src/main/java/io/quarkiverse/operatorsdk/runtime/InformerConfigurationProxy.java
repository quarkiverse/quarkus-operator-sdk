package io.quarkiverse.operatorsdk.runtime;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.cache.ItemStore;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.processing.GroupVersionKind;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryToSecondaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnDeleteFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;

public class InformerConfigurationProxy<R extends HasMetadata> extends InformerConfiguration.DefaultInformerConfiguration<R> {
    public InformerConfigurationProxy(String name, String labelSelector, Class<R> resourceClass,
            GroupVersionKind groupVersionKind, PrimaryToSecondaryMapper<?> primaryToSecondaryMapper,
            SecondaryToPrimaryMapper<R> secondaryToPrimaryMapper, Set<String> namespaces,
            boolean followControllerNamespaceChanges, OnAddFilter<? super R> onAddFilter,
            OnUpdateFilter<? super R> onUpdateFilter, OnDeleteFilter<? super R> onDeleteFilter,
            GenericFilter<? super R> genericFilter, ItemStore<R> itemStore, Long informerListLimit) {
        super(name, labelSelector, resourceClass, groupVersionKind,
                primaryToSecondaryMapper, secondaryToPrimaryMapper,
                namespaces, followControllerNamespaceChanges, onAddFilter, onUpdateFilter, onDeleteFilter, genericFilter,
                itemStore, informerListLimit);
    }

    public OnAddFilter<? super R> getOnAddFilter() {
        return onAddFilter().orElse(null);
    }

    public OnDeleteFilter<? super R> getOnDeleteFilter() {
        return onDeleteFilter().orElse(null);
    }

    public OnUpdateFilter<? super R> getOnUpdateFilter() {
        return onUpdateFilter().orElse(null);
    }

    public GenericFilter<? super R> getGenericFilter() {
        return genericFilter().orElse(null);
    }

    public String getName() {
        return name();
    }

    public boolean isFollowControllerNamespaceChanges() {
        return followControllerNamespaceChanges();
    }

    public static class PrimaryToSecondaryMapperProxy<P extends HasMetadata> implements PrimaryToSecondaryMapper<P> {
        private final PrimaryToSecondaryMapper<P> mapper;

        public PrimaryToSecondaryMapperProxy(PrimaryToSecondaryMapper<P> mapper) {
            this.mapper = mapper;
        }

        public PrimaryToSecondaryMapper<P> getMapper() {
            return mapper;
        }

        @Override
        public Set<ResourceID> toSecondaryResourceIDs(P p) {
            return mapper.toSecondaryResourceIDs(p);
        }
    }

    public static class SecondaryToPrimaryMapperProxy<P extends HasMetadata> implements SecondaryToPrimaryMapper<P> {
        private final SecondaryToPrimaryMapper<P> mapper;

        public SecondaryToPrimaryMapperProxy(SecondaryToPrimaryMapper<P> mapper) {
            this.mapper = mapper;
        }

        public SecondaryToPrimaryMapper<P> getMapper() {
            return mapper;
        }

        @Override
        public Set<ResourceID> toPrimaryResourceIDs(P p) {
            return mapper.toPrimaryResourceIDs(p);
        }
    }
}
