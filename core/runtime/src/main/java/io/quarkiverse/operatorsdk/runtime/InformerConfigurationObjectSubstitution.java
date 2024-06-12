package io.quarkiverse.operatorsdk.runtime;

import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.quarkus.runtime.ObjectSubstitution;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class InformerConfigurationObjectSubstitution
        implements ObjectSubstitution<InformerConfiguration, InformerConfigurationProxy> {
    private static final Logger log = Logger.getLogger(InformerConfigurationObjectSubstitution.class);

    @Override
    public InformerConfigurationProxy serialize(InformerConfiguration obj) {
        log.info("Serializing InformerConfiguration: " + obj);
        return _serialize(obj);
    }

    private static <R extends HasMetadata> InformerConfigurationProxy<R> _serialize(InformerConfiguration<R> obj) {
        return new InformerConfigurationProxy<>(obj.name(), obj.getLabelSelector(), obj.getResourceClass(),
                obj.getGroupVersionKind().orElse(null),
                new InformerConfigurationProxy.PrimaryToSecondaryMapperProxy<>(obj.getPrimaryToSecondaryMapper()),
                new InformerConfigurationProxy.SecondaryToPrimaryMapperProxy<>(obj.getSecondaryToPrimaryMapper()),
                obj.getNamespaces(), obj.followControllerNamespaceChanges(),
                obj.onAddFilter().orElse(null), obj.onUpdateFilter().orElse(null), obj.onDeleteFilter().orElse(null),
                obj.genericFilter().orElse(null), obj.getItemStore().orElse(null), obj.getInformerListLimit().orElse(null));
    }

    @Override
    public InformerConfiguration deserialize(InformerConfigurationProxy obj) {
        log.info("Deserializing InformerConfiguration: " + obj);
        return obj;
    }
}
