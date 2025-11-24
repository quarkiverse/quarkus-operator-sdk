package io.quarkiverse.operatorsdk.it.cdi;

import java.util.Map;

import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDNoGCKubernetesDependentResource;

public class DeploymentDependent extends CRUDNoGCKubernetesDependentResource<Deployment, TestResource> {

    private static final Logger LOG = Logger.getLogger(DeploymentDependent.class);

    @Override
    protected Deployment desired(TestResource primary, Context<TestResource> context) {
        return new DeploymentBuilder()
                .withMetadata(new ObjectMetaBuilder()
                        .withName(primary.getMetadata().getName() + "-deployment")
                        .withNamespace("default")
                        .withLabels(Map.of("app", primary.getMetadata().getName()))
                        .build())
                .withNewSpec()
                .withNewSelector().withMatchLabels(Map.of("app", primary.getMetadata().getName())).endSelector()
                .withNewTemplate().withNewMetadata().withLabels(Map.of("app", primary.getMetadata().getName())).endMetadata()
                .withNewSpec().addNewContainer()
                .withName("nginx").withImage("nginx:1.14.2")
                .addNewPort().withName("http").withProtocol("TCP").withContainerPort(8080).endPort()
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
    }
}
