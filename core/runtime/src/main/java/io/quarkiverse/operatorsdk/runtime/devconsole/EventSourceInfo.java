package io.quarkiverse.operatorsdk.runtime.devconsole;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.source.Configurable;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

public class EventSourceInfo implements Comparable<EventSourceInfo> {
    private final EventSource<?, ? extends HasMetadata> metadata;

    public EventSourceInfo(EventSource<?, ? extends HasMetadata> metadata) {
        this.metadata = metadata;
    }

    public String getName() {
        return metadata.name();
    }

    @SuppressWarnings("unused")
    public String getResourceClass() {
        return metadata.resourceType().getName();
    }

    public String getType() {
        return metadata.getClass().getName();
    }

    public Optional<?> getConfiguration() {
        return metadata instanceof Configurable<?> ? Optional.of(((Configurable<?>) metadata).configuration())
                : Optional.empty();
    }

    @Override
    public int compareTo(EventSourceInfo other) {
        return getName().compareTo(other.getName());
    }
}
