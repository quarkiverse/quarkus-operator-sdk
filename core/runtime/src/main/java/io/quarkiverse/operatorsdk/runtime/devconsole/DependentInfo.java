package io.quarkiverse.operatorsdk.runtime.devconsole;

import java.util.Optional;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import io.javaoperatorsdk.operator.processing.event.EventSourceMetadata;
import io.javaoperatorsdk.operator.processing.event.source.Configurable;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventSource;
import io.quarkus.arc.Arc;

@SuppressWarnings({ "unused", "rawtypes" })
public class DependentInfo<R, P extends HasMetadata> implements Comparable<DependentInfo> {
    private final DependentResourceSpec<R, P> spec;
    private final EventSourceContext<P> context;

    public DependentInfo(DependentResourceSpec<R, P> spec, EventSourceContext<P> context) {
        this.spec = spec;
        this.context = context;
    }

    EventSource eventSource() {
        // todo: fix-me
        final DependentResource<R, P> dependent = Arc.container()
                .instance(spec.getDependentResourceClass()).get();
        return dependent.eventSource(context).orElse(null);
    }

    public EventSourceInfo getEventSource() {
        return Optional.ofNullable(eventSource()).map(es -> new EventSourceMetadata() {
            @Override
            public String name() {
                return DependentInfo.this.getName();
            }

            @Override
            public Class<?> type() {
                return es.getClass();
            }

            @Override
            public Optional<Class<?>> resourceType() {
                return es instanceof ResourceEventSource
                        ? Optional.of(((ResourceEventSource<?, ?>) es).resourceType())
                        : Optional.empty();
            }

            @Override
            public Optional<?> configuration() {
                return es instanceof Configurable
                        ? Optional.of(((Configurable<?>) es).configuration())
                        : Optional.empty();
            }

        }).map(EventSourceInfo::new).orElse(null);
    }

    public String getResourceClass() {
        // todo: fix-me
        final DependentResource<R, P> dependent = Arc.container()
                .instance(spec.getDependentResourceClass()).get();
        return dependent.resourceType().getName();
    }

    public Optional<?> getDependentResourceConfiguration() {
        return null; // todo
    }

    public String getName() {
        return spec.getName();
    }

    public Set<String> getDependsOn() {
        return spec.getDependsOn();
    }

    public boolean getHasConditions() {
        return getReadyCondition() != null || getReconcileCondition() != null || getDeletePostCondition() != null;
    }

    public String getReadyCondition() {
        return getConditionClassName(spec.getReadyCondition());
    }

    public String getReconcileCondition() {
        return getConditionClassName(spec.getReconcileCondition());
    }

    public String getDeletePostCondition() {
        return getConditionClassName(spec.getDeletePostCondition());
    }

    private String getConditionClassName(Condition condition) {
        return condition != null ? condition.getClass().getName() : null;
    }

    public String getUseEventSourceWithName() {
        return spec.getUseEventSourceWithName().orElse(null);
    }

    public String getType() {
        return spec.getDependentResourceClass().getName();
    }

    @Override
    public int compareTo(DependentInfo other) {
        return getName().compareTo(other.getName());
    }
}
