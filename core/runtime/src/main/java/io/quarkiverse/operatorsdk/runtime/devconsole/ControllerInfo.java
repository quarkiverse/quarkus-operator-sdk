package io.quarkiverse.operatorsdk.runtime.devconsole;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.Controller;

public class ControllerInfo<P extends HasMetadata> {
    private final Controller<P> controller;
    private final Set<EventSourceInfo> eventSources;
    private final Set<DependentInfo> dependents;

    public ControllerInfo(Controller<P> controller) {
        this.controller = controller;
        final var context = new EventSourceContext<>(controller.getEventSourceManager().getControllerResourceEventSource(),
                controller.getConfiguration(), controller.getClient());
        dependents = controller.getConfiguration().getDependentResources().stream()
                .map(spec -> new DependentInfo(spec, context))
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));
        eventSources = controller.getEventSourceManager().getNamedEventSourcesStream()
                .map(EventSourceInfo::new)
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public String getName() {
        return controller.getConfiguration().getName();
    }

    @SuppressWarnings("unused")
    public String getClassName() {
        return controller.getConfiguration().getAssociatedReconcilerClassName();
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
    public Set<String> getConfiguredNamespaces() {
        return controller.getConfiguration().getNamespaces();
    }

    @SuppressWarnings("unused")
    public Set<EventSourceInfo> getEventSources() {
        return eventSources;
    }

    public Set<DependentInfo> getDependents() {
        return dependents;
    }

    @SuppressWarnings("unused")
    public List<P> getKnownResources() {
        return controller.getEventSourceManager().getControllerResourceEventSource().list().collect(
                Collectors.toList());
    }
}
