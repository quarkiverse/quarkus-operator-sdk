package io.quarkiverse.operatorsdk.runtime;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.AbstractConfigurationService;
import io.javaoperatorsdk.operator.api.config.Cloner;
import io.javaoperatorsdk.operator.api.config.Version;
import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.quarkus.arc.runtime.ClientProxyUnwrapper;

public class QuarkusConfigurationService extends AbstractConfigurationService {
    private static final Logger log = LoggerFactory.getLogger(QuarkusConfigurationService.class);

    private static final ClientProxyUnwrapper unwrapper = new ClientProxyUnwrapper();
    private final KubernetesClient client;
    private final CRDGenerationInfo crdInfo;
    private final int concurrentReconciliationThreads;
    private final Cloner cloner;
    private final int terminationTimeout;
    private final Map<String, String> reconcilerClassToName;
    private final Metrics metrics;
    private final boolean startOperator;

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public QuarkusConfigurationService(
            Version version,
            Collection<QuarkusControllerConfiguration> configurations,
            KubernetesClient client,
            CRDGenerationInfo crdInfo, int maxThreads,
            int timeout, ObjectMapper mapper, Metrics metrics, boolean startOperator) {
        super(version);
        this.startOperator = startOperator;
        this.client = client;
        this.cloner = new Cloner() {
            @Override
            public <R extends HasMetadata> R clone(R r) {
                try {
                    return (R) mapper.readValue(mapper.writeValueAsString(r), r.getClass());
                } catch (JsonProcessingException e) {
                    throw new IllegalStateException(e);
                }
            }
        };
        this.metrics = metrics;
        if (configurations != null && !configurations.isEmpty()) {
            reconcilerClassToName = new HashMap<>(configurations.size());
            configurations.forEach(c -> {
                reconcilerClassToName.put(c.getAssociatedReconcilerClassName(), c.getName());
                register(c);
            });
        } else {
            reconcilerClassToName = Collections.emptyMap();
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
    public <R extends HasMetadata> QuarkusControllerConfiguration<R> getConfigurationFor(Reconciler<R> reconciler) {
        final var unwrapped = unwrap(reconciler);
        return (QuarkusControllerConfiguration<R>) super.getConfigurationFor(unwrapped);
    }

    @Override
    public boolean checkCRDAndValidateLocalModel() {
        return crdInfo.isValidateCRDs();
    }

    @SuppressWarnings("unchecked")
    private static <R extends HasMetadata> Reconciler<R> unwrap(Reconciler<R> reconciler) {
        return (Reconciler<R>) unwrapper.apply(reconciler);
    }

    @Override
    protected String keyFor(Reconciler controller) {
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
        String controllerName = reconcilerClassToName.get(controllerClass);
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
    public Cloner getResourceCloner() {
        return cloner;
    }

    @Override
    public int getTerminationTimeoutSeconds() {
        return terminationTimeout;
    }

    public CRDGenerationInfo getCRDGenerationInfo() {
        return crdInfo;
    }

    @Override
    protected void logMissingReconcilerWarning(String reconcilerKey, String reconcilersNameMessage) {
        log.warn("Cannot find configuration for '{}' reconciler. {}", reconcilerKey, reconcilersNameMessage);
    }

    @Override
    public Metrics getMetrics() {
        return metrics;
    }

    KubernetesClient getClient() {
        return client;
    }

    boolean shouldStartOperator() {
        return startOperator;
    }
}
