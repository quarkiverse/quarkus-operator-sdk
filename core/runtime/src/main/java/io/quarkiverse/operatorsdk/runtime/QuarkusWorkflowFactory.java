package io.quarkiverse.operatorsdk.runtime;

import io.javaoperatorsdk.operator.processing.dependent.workflow.ManagedWorkflow;
import io.javaoperatorsdk.operator.processing.dependent.workflow.ManagedWorkflowFactory;

public class QuarkusWorkflowFactory implements ManagedWorkflowFactory<QuarkusControllerConfiguration<?>> {
    @Override
    public ManagedWorkflow<?> workflowFor(QuarkusControllerConfiguration<?> controllerConfiguration) {
        return controllerConfiguration.getWorkflow();
    }
}
