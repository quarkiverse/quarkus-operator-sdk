package io.quarkiverse.operatorsdk.runtime.devconsole;

import java.util.Collection;
import java.util.stream.Collectors;

import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

public class EventSourceInfo {
    private final EventSource eventSource;

    public EventSourceInfo(EventSource eventSource) {
        this.eventSource = eventSource;
    }

    @SuppressWarnings({ "unused", "unchecked" })
    public Collection<ResourceID> getItems() {
        return (Collection<ResourceID>) asInformer().keys().collect(Collectors.toList());
    }

    @SuppressWarnings("unused")
    public Class<?> getResourceType() {
        return asInformer().resourceType();
    }

    @SuppressWarnings({ "rawtypes" })
    private InformerEventSource asInformer() {
        return (InformerEventSource) eventSource;
    }

    @Override
    public String toString() {
        if (eventSource instanceof InformerEventSource) {
            final var informer = asInformer();
            final var configuration = informer.getConfiguration();
            return String.format("Informer (%b) -> %s\n\t- onAdd: %s\n\t- onUpdate: %s\n\t- onDelete: %s\n\t- generic: %s",
                    informer.isRunning(),
                    configuration.getResourceClass(),
                    configuration.onAddFilter(),
                    configuration.onUpdateFilter(),
                    configuration.onDeleteFilter(),
                    configuration.genericFilter());
        }
        return super.toString();
    }
}
