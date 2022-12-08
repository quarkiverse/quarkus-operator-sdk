package io.quarkiverse.operatorsdk.runtime;

import java.util.List;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.processing.dependent.workflow.DefaultManagedWorkflow;
import io.javaoperatorsdk.operator.processing.dependent.workflow.ManagedWorkflow;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Workflow;
import io.quarkus.runtime.annotations.RecordableConstructor;

@SuppressWarnings("rawtypes")
public class QuarkusManagedWorkflow<P extends HasMetadata> extends DefaultManagedWorkflow<P> {

    public static final Workflow noOpWorkflow = new NoOpWorkflow();
    public static final ManagedWorkflow noOpManagedWorkflow = new NoOpManagedWorkflow();

    public static class NoOpWorkflow<P extends HasMetadata> implements Workflow<P> {
    }

    public static class NoOpManagedWorkflow<P extends HasMetadata> implements ManagedWorkflow<P> {

        @Override
        @SuppressWarnings("unchecked")
        public Workflow<P> resolve(KubernetesClient kubernetesClient,
                ControllerConfiguration<P> controllerConfiguration) {
            return noOpWorkflow;
        }
    }

    @RecordableConstructor
    public QuarkusManagedWorkflow(List<DependentResourceSpec<?, ?>> orderedSpecs,
            boolean hasCleaner) {
        super(orderedSpecs, hasCleaner);
    }

    // Needed for the recordable constructor
    @SuppressWarnings("unused")
    public boolean isHasCleaner() {
        return hasCleaner();
    }
}
