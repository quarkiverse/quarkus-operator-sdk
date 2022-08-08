package io.quarkiverse.operatorsdk.runtime.devconsole;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.Controller;

public class ControllerInfo<P extends HasMetadata> {
    private final Controller<P> controller;

    public ControllerInfo(Controller<P> controller) {
        this.controller = controller;
    }

    public String getName() {
        return controller.getConfiguration().getName();
    }

    @SuppressWarnings("unused")
    public Class<P> getResourceClass() {
        return controller.getConfiguration().getResourceClass();
    }

    @SuppressWarnings("unused")
    public Set<String> getEffectiveNamespaces() {
        return controller.getConfiguration().getEffectiveNamespaces();
    }

    @SuppressWarnings("unused")
    public Set<EventSourceInfo> getEventSources() {
        return controller.getEventSourceManager().getRegisteredEventSources().stream()
                .map(EventSourceInfo::new)
                .collect(Collectors.toSet());
    }

    @SuppressWarnings("unused")
    public List<P> getKnownResources() {
        return controller.getEventSourceManager().getControllerResourceEventSource().list().collect(
                Collectors.toList());
    }
}
