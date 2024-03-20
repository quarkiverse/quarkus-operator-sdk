package io.quarkiverse.operatorsdk.runtime;

import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.config.workflow.WorkflowSpec;
import io.javaoperatorsdk.operator.processing.dependent.workflow.DefaultManagedWorkflow;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Workflow;
import io.quarkus.runtime.annotations.IgnoreProperty;
import io.quarkus.runtime.annotations.RecordableConstructor;

@SuppressWarnings("rawtypes")
public class QuarkusManagedWorkflow<P extends HasMetadata> extends DefaultManagedWorkflow<P> {
    private final QuarkusWorkflowSpec spec;

    public static final Workflow noOpWorkflow = new NoOpWorkflow();
    public static final QuarkusManagedWorkflow noOpManagedWorkflow = new NoOpManagedWorkflow();

    public static class NoOpWorkflow<P extends HasMetadata> implements Workflow<P> {
    }

    public static class NoOpManagedWorkflow<P extends HasMetadata> extends QuarkusManagedWorkflow<P> {

        @Override
        @SuppressWarnings("unchecked")
        public Workflow<P> resolve(KubernetesClient kubernetesClient,
                ControllerConfiguration<P> controllerConfiguration) {
            return noOpWorkflow;
        }
    }

    private QuarkusManagedWorkflow() {
        this(null, List.of(), false);
    }

    @RecordableConstructor
    public QuarkusManagedWorkflow(QuarkusWorkflowSpec nullableSpec, List<DependentResourceSpec> orderedSpecs,
            boolean hasCleaner) {
        super(orderedSpecs, hasCleaner);
        this.spec = nullableSpec;
    }

    // Needed for the recordable constructor
    @SuppressWarnings("unused")
    public boolean isHasCleaner() {
        return hasCleaner();
    }

    // Needed for the recordable constructor
    @SuppressWarnings("unused")
    public QuarkusWorkflowSpec getNullableSpec() {
        return spec;
    }

    @IgnoreProperty
    public Optional<QuarkusWorkflowSpec> getSpec() {
        return Optional.ofNullable(spec);
    }

    @IgnoreProperty
    public Optional<WorkflowSpec> getGenericSpec() {
        return Optional.ofNullable(spec);
    }
}
