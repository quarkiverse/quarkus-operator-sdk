package io.quarkiverse.operatorsdk.runtime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResourceFactory;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import io.javaoperatorsdk.operator.processing.dependent.workflow.DependentResourceNode;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.ClientProxy;
import io.quarkus.arc.InstanceHandle;

public class QuarkusDependentResourceFactory
        implements DependentResourceFactory<QuarkusControllerConfiguration<?>, DependentResourceSpecMetadata<?, ?, ?>> {
    @SuppressWarnings("rawtypes")
    private final Map<String, DependentResource> knownDependents = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public DependentResource createFrom(DependentResourceSpecMetadata spec,
            QuarkusControllerConfiguration configuration) {
        final var dependentKey = getDependentKey(configuration, spec);
        var dependentResource = knownDependents.get(dependentKey);
        if (dependentResource == null) {
            final Class<? extends DependentResource<?, ?>> dependentResourceClass = spec.getDependentResourceClass();
            try (final var dependentInstance = Arc.container().instance(dependentResourceClass)) {
                final var dependent = dependentInstance.get();

                if (dependent == null) {
                    throw new IllegalStateException(
                            "Couldn't find bean associated with DependentResource "
                                    + dependentResourceClass.getName());
                }

                dependentResource = ClientProxy.unwrap(dependent);
                // configure the bean
                configure(dependentResource, spec, configuration);
                // record the configured dependent for later retrieval if needed
                knownDependents.put(dependentKey, dependentResource);
            }
        }
        return dependentResource;
    }

    @Override
    public void configure(DependentResource instance, DependentResourceSpecMetadata<?, ?, ?> spec,
            QuarkusControllerConfiguration<?> controllerConfiguration) {
        DependentResourceFactory.super.configure(instance, spec, controllerConfiguration);
    }

    @Override
    public Class<?> associatedResourceType(DependentResourceSpecMetadata<?, ?, ?> spec) {
        return spec.getResourceClass();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public DependentResourceNode createNodeFrom(DependentResourceSpecMetadata<?, ?, ?> spec,
            DependentResource dependentResource) {
        final var container = Arc.container();
        if (container != null) {
            return new DependentResourceNode(
                    getBeanInstanceOrDefault(container, spec.getReconcileCondition()),
                    getBeanInstanceOrDefault(container, spec.getDeletePostCondition()),
                    getBeanInstanceOrDefault(container, spec.getReadyCondition()),
                    getBeanInstanceOrDefault(container, spec.getActivationCondition()),
                    dependentResource);
        } else {
            return DependentResourceFactory.super.createNodeFrom(spec, dependentResource);
        }
    }

    @SuppressWarnings("rawtypes")
    private Condition getBeanInstanceOrDefault(ArcContainer container, Condition reconcileCondition) {
        if (reconcileCondition == null) {
            return null;
        }
        try (InstanceHandle<? extends Condition> instance = container.instance(reconcileCondition.getClass())) {
            if (instance.isAvailable()) {
                return instance.get();
            } else {
                return reconcileCondition;
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private static String getDependentKey(QuarkusControllerConfiguration configuration,
            DependentResourceSpec spec) {
        return getDependentKeyFromNames(configuration.getName(), spec.getName());
    }

    private static String getDependentKeyFromNames(String controllerName, String dependentName) {
        return controllerName + "#" + dependentName;
    }
}
