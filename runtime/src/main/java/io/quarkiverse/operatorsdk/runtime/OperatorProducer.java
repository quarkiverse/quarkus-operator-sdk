package io.quarkiverse.operatorsdk.runtime;

import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.quarkus.arc.DefaultBean;

@Singleton
public class OperatorProducer {

    @Produces
    @DefaultBean
    @Singleton
    Operator operator(KubernetesClient client, QuarkusConfigurationService configuration,
            Instance<ResourceController<? extends CustomResource>> controllers) {
        final var operator = new Operator(client, configuration);
        for (ResourceController<? extends CustomResource> controller : controllers) {
            QuarkusControllerConfiguration<? extends CustomResource> config = configuration
                    .getConfigurationFor(controller);
            if (!config.isRegistrationDelayed()) {
                operator.register(controller);
            }
        }
        return operator;
    }
}
