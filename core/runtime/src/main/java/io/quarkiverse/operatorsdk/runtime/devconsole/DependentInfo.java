package io.quarkiverse.operatorsdk.runtime.devconsole;

import java.util.Optional;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import io.javaoperatorsdk.operator.processing.event.EventSourceMetadata;
import io.javaoperatorsdk.operator.processing.event.source.Configurable;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventSource;

@SuppressWarnings({"unused", "rawtypes"})
public class DependentInfo<R, P extends HasMetadata> {
    private final DependentResourceSpec<R, P, ?> spec;
    private final EventSourceContext<P> context;

    public DependentInfo(DependentResourceSpec<R, P, ?> spec, EventSourceContext<P> context) {
        this.spec = spec;
        this.context = context;
    }

    EventSource eventSource() {
        return spec.getDependentResource().eventSource(context).orElse(null);
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
        return spec.getDependentResource().resourceType().getName();
    }

    public Optional<?> getDependentResourceConfiguration() {
        return spec.getDependentResourceConfiguration();
    }

    public String getName() {
        return spec.getName();
    }

    public Set<String> getDependsOn() {
        return spec.getDependsOn();
    }

    public Condition getReadyCondition() {
        return spec.getReadyCondition();
    }

    public Condition getReconcileCondition() {
        return spec.getReconcileCondition();
    }

    public Condition getDeletePostCondition() {
        return spec.getDeletePostCondition();
    }

    public Optional<String> getUseEventSourceWithName() {
        return spec.getUseEventSourceWithName();
    }

    public String getType() {
        return spec.getDependentResourceClass().getName();
    }
}
