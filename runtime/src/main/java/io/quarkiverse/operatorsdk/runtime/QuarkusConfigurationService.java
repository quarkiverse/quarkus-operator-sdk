package io.quarkiverse.operatorsdk.runtime;

import java.util.List;
import java.util.stream.Stream;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.config.AbstractConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.Version;
import io.quarkus.arc.runtime.ClientProxyUnwrapper;

public class QuarkusConfigurationService extends AbstractConfigurationService {

    private static final ClientProxyUnwrapper unwrapper = new ClientProxyUnwrapper();
    private final KubernetesClient client;
    private final boolean checkCRDAndValidateLocalModel;

    public QuarkusConfigurationService(
            Version version,
            List<ControllerConfiguration> configurations,
            KubernetesClient client,
            boolean checkCRDAndValidateLocalModel) {
        super(version);
        this.client = client;
        if (configurations != null && !configurations.isEmpty()) {
            configurations.forEach(this::register);
        }
        this.checkCRDAndValidateLocalModel = checkCRDAndValidateLocalModel;
    }

    @Override
    public Config getClientConfiguration() {
        return client.getConfiguration();
    }

    @Override
    public <R extends CustomResource> QuarkusControllerConfiguration<R> getConfigurationFor(
            ResourceController<R> controller) {
        final var unwrapped = unwrap(controller);
        return (QuarkusControllerConfiguration<R>) super.getConfigurationFor(unwrapped);
    }

    @Override
    public boolean checkCRDAndValidateLocalModel() {
        return checkCRDAndValidateLocalModel;
    }

    private static <R extends CustomResource> ResourceController<R> unwrap(
            ResourceController<R> controller) {
        return (ResourceController<R>) unwrapper.apply(controller);
    }

    @Override
    protected String keyFor(ResourceController controller) {
        String controllerName = super.keyFor(controller);
        // heuristics: we're assuming that any class name with an '_' in it is a
        // proxy / wrapped / generated class and that the "real" class name is located before the
        // '_'. This probably won't work in all instances but should work most of the time.
        final int i = controllerName.indexOf('_');
        if (i > 0) {
            controllerName = controllerName.substring(0, i);
        }
        return controllerName;
    }

    public Stream<QuarkusControllerConfiguration> configurations() {
        return controllerConfigurations().map(c -> (QuarkusControllerConfiguration) c);
    }

    public void updateConfiguration(QuarkusControllerConfiguration configuration) {
        replace(configuration);
    }
}
