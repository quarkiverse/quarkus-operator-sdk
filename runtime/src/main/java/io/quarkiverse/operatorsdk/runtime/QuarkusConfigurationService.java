package io.quarkiverse.operatorsdk.runtime;

import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.config.AbstractConfigurationService;
import io.javaoperatorsdk.operator.api.config.Version;
import io.quarkus.arc.runtime.ClientProxyUnwrapper;

public class QuarkusConfigurationService extends AbstractConfigurationService {

    private static final ClientProxyUnwrapper unwrapper = new ClientProxyUnwrapper();
    private final KubernetesClient client;
    private final CRDGenerationInfo crdInfo;
    private final int concurrentReconciliationThreads;
    private final ObjectMapper mapper;
    private int terminationTimeout;
    private final Map<String, String> controllerClassToName;

    public QuarkusConfigurationService(
            Version version,
            List<QuarkusControllerConfiguration> configurations,
            KubernetesClient client,
            CRDGenerationInfo crdInfo, int maxThreads,
            int timeout, ObjectMapper mapper) {
        super(version);
        this.client = client;
        this.mapper = mapper;
        if (configurations != null && !configurations.isEmpty()) {
            controllerClassToName = new HashMap<>(configurations.size());
            configurations.forEach(c -> {
                controllerClassToName.put(c.getAssociatedControllerClassName(), c.getName());
                register(c);
            });
        } else {
            controllerClassToName = Collections.emptyMap();
        }
        this.crdInfo = crdInfo;
        this.concurrentReconciliationThreads = maxThreads;
        this.terminationTimeout = timeout;
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
        return crdInfo.isValidateCRDs();
    }

    private static <R extends CustomResource> ResourceController<R> unwrap(
            ResourceController<R> controller) {
        return (ResourceController<R>) unwrapper.apply(controller);
    }

    @Override
    protected String keyFor(ResourceController controller) {
        // retrieve the controller name from its associated class name
        // but we first need to check if the class is wrapped
        // heuristics: we're assuming that any class name with an '_' in it is a
        // proxy / wrapped / generated class and that the "real" class name is located before the
        // '_'. This probably won't work in all instances but should work most of the time.
        var controllerClass = controller.getClass().getName();
        final int i = controllerClass.indexOf('_');
        if (i > 0) {
            controllerClass = controllerClass.substring(0, i);
        }
        String controllerName = controllerClassToName.get(controllerClass);
        if (controllerName == null) {
            throw new IllegalArgumentException("Unknown controller " + controllerClass);
        }
        return controllerName;
    }

    @Override
    public int concurrentReconciliationThreads() {
        return this.concurrentReconciliationThreads;
    }

    @Override
    public ObjectMapper getObjectMapper() {
        return this.mapper;
    }

    @Override
    public int getTerminationTimeoutSeconds() {
        return terminationTimeout;
    }

    public CRDGenerationInfo getCRDGenerationInfo() {
        return crdInfo;
    }
}
