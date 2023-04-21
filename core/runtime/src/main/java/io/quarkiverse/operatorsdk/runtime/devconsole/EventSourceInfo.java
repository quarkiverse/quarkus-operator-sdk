package io.quarkiverse.operatorsdk.runtime.devconsole;

import io.javaoperatorsdk.operator.processing.event.EventSourceMetadata;

public class EventSourceInfo implements Comparable<EventSourceInfo> {
    private final EventSourceMetadata metadata;

    public EventSourceInfo(EventSourceMetadata metadata) {
        this.metadata = metadata;
    }

    public String getName() {
        return metadata.name();
    }

    @SuppressWarnings("unused")
    public String getResourceClass() {
        return metadata.resourceType().map(Class::getName).orElse(null);
    }

    public String getType() {
        return metadata.type().getName();
    }

    @Override
    public int compareTo(EventSourceInfo other) {
        return getName().compareTo(other.getName());
    }
}
