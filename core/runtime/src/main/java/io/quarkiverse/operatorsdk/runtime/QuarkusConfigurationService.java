package io.quarkiverse.operatorsdk.runtime;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.AbstractConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.InformerStoppedHandler;
import io.javaoperatorsdk.operator.api.config.LeaderElectionConfiguration;
import io.javaoperatorsdk.operator.api.config.Version;
import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResourceFactory;
import io.javaoperatorsdk.operator.processing.dependent.workflow.ManagedWorkflow;
import io.javaoperatorsdk.operator.processing.dependent.workflow.ManagedWorkflowFactory;
import io.quarkus.arc.ClientProxy;

public class QuarkusConfigurationService extends AbstractConfigurationService implements
        ManagedWorkflowFactory<QuarkusControllerConfiguration<?>> {
    private static final Logger log = LoggerFactory.getLogger(QuarkusConfigurationService.class);
    private final CRDGenerationInfo crdInfo;
    private final int concurrentReconciliationThreads;
    private final Map<String, String> reconcilerClassToName;
    private final Metrics metrics;
    private final boolean startOperator;
    private final LeaderElectionConfiguration leaderElectionConfiguration;
    private final InformerStoppedHandler informerStoppedHandler;
    private final boolean closeClientOnStop;
    private final boolean stopOnInformerErrorDuringStartup;
    private final int concurrentWorkflowExecutorThreads;
    private final Duration cacheSyncTimeout;
    private final Duration reconciliationTimeout;
    private final boolean asyncStart;
    private final boolean useSSA;
    private final boolean defensiveCloning;
    private DependentResourceFactory<QuarkusControllerConfiguration<?>, DependentResourceSpecMetadata<?, ?, ?>> dependentResourceFactory;

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public QuarkusConfigurationService(
            Version version,
            Collection<QuarkusControllerConfiguration> configurations,
            KubernetesClient kubernetesClient,
            CRDGenerationInfo crdInfo, int maxThreads, int maxWorflowThreads,
            Duration reconciliationTimeout, Duration cacheSyncTimeout, boolean asyncStart, Metrics metrics,
            boolean startOperator,
            LeaderElectionConfiguration leaderElectionConfiguration, InformerStoppedHandler informerStoppedHandler,
            boolean closeClientOnStop, boolean stopOnInformerErrorDuringStartup,
            boolean useSSA, boolean defensiveCloning) {
        super(version);
        this.closeClientOnStop = closeClientOnStop;
        this.stopOnInformerErrorDuringStartup = stopOnInformerErrorDuringStartup;
        init(null, null, kubernetesClient);
        this.startOperator = startOperator;
        this.metrics = metrics;
        if (configurations != null && !configurations.isEmpty()) {
            final var size = configurations.size();
            reconcilerClassToName = new HashMap<>(size);
            configurations.forEach(c -> {
                final var name = c.getName();
                reconcilerClassToName.put(c.getAssociatedReconcilerClassName(), name);
                register(c);
                c.setParent(this);
            });
        } else {
            reconcilerClassToName = Collections.emptyMap();
        }
        this.crdInfo = crdInfo;
        this.concurrentReconciliationThreads = maxThreads;
        this.concurrentWorkflowExecutorThreads = maxWorflowThreads;
        this.reconciliationTimeout = reconciliationTimeout;
        this.cacheSyncTimeout = cacheSyncTimeout;
        this.asyncStart = asyncStart;
        this.informerStoppedHandler = informerStoppedHandler;
        this.leaderElectionConfiguration = leaderElectionConfiguration;
        this.useSSA = useSSA;
        this.defensiveCloning = defensiveCloning;
    }

    private static <R extends HasMetadata> Reconciler<R> unwrap(Reconciler<R> reconciler) {
        return ClientProxy.unwrap(reconciler);
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
    public Duration reconciliationTerminationTimeout() {
        return getReconciliationTimeout();
    }

    public Duration getReconciliationTimeout() {
        return reconciliationTimeout;
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

    boolean shouldStartOperator() {
        return startOperator;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Stream<ControllerConfiguration> controllerConfigurations() {
        return super.controllerConfigurations();
    }

    @Override
    public Optional<LeaderElectionConfiguration> getLeaderElectionConfiguration() {
        return Optional.ofNullable(leaderElectionConfiguration);
    }

    @Override
    public Optional<InformerStoppedHandler> getInformerStoppedHandler() {
        return Optional.ofNullable(informerStoppedHandler);
    }

    @Override
    public int concurrentWorkflowExecutorThreads() {
        return concurrentWorkflowExecutorThreads;
    }

    @Override
    public boolean closeClientOnStop() {
        return closeClientOnStop;
    }

    @Override
    public boolean stopOnInformerErrorDuringStartup() {
        return stopOnInformerErrorDuringStartup;
    }

    @Override
    public Duration cacheSyncTimeout() {
        return cacheSyncTimeout;
    }

    public boolean isAsyncStart() {
        return asyncStart;
    }

    @Override
    public DependentResourceFactory<QuarkusControllerConfiguration<?>, DependentResourceSpecMetadata<?, ?, ?>> dependentResourceFactory() {
        if (dependentResourceFactory == null) {
            dependentResourceFactory = new QuarkusDependentResourceFactory();
        }
        return dependentResourceFactory;
    }

    @Override
    public ManagedWorkflowFactory<QuarkusControllerConfiguration<?>> getWorkflowFactory() {
        return this;
    }

    @Override
    public ManagedWorkflow<?> workflowFor(QuarkusControllerConfiguration<?> controllerConfiguration) {
        return controllerConfiguration.getWorkflow();
    }

    @SuppressWarnings({ "rawtypes" })
    public DependentResourceSpecMetadata getDependentByName(String controllerName, String dependentName) {
        final ControllerConfiguration<?> cc = getFor(controllerName);
        if (cc == null) {
            return null;
        } else {
            return cc.getWorkflowSpec().flatMap(spec -> spec.getDependentResourceSpecs().stream()
                    .filter(r -> r.getName().equals(dependentName) && r instanceof DependentResourceSpecMetadata<?, ?, ?>)
                    .map(DependentResourceSpecMetadata.class::cast)
                    .findFirst())
                    .orElse(null);
        }
    }

    @SuppressWarnings("rawtypes")
    public ManagedWorkflow workflowByName(String name) {
        return ((QuarkusControllerConfiguration) getFor(name)).getWorkflow();
    }

    @Override
    public boolean ssaBasedCreateUpdateMatchForDependentResources() {
        return useSSA;
    }

    @Override
    public boolean useSSAToPatchPrimaryResource() {
        return useSSA;
    }

    @Override
    public boolean cloneSecondaryResourcesWhenGettingFromCache() {
        return defensiveCloning;
    }
}
