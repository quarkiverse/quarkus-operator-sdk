package io.quarkiverse.operatorsdk.runtime;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
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
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceConfigurationResolver;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResourceFactory;
import io.javaoperatorsdk.operator.processing.dependent.workflow.ManagedWorkflow;
import io.javaoperatorsdk.operator.processing.dependent.workflow.ManagedWorkflowFactory;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;

public class QuarkusConfigurationService extends AbstractConfigurationService implements
        DependentResourceFactory<QuarkusControllerConfiguration<?>>,
        ManagedWorkflowFactory<QuarkusControllerConfiguration<?>> {
    private static final Logger log = LoggerFactory.getLogger(QuarkusConfigurationService.class);
    private final CRDGenerationInfo crdInfo;
    private final int concurrentReconciliationThreads;
    private final int terminationTimeout;
    private final Map<String, String> reconcilerClassToName;
    private final Metrics metrics;
    private final boolean startOperator;
    private final LeaderElectionConfiguration leaderElectionConfiguration;
    private final InformerStoppedHandler informerStoppedHandler;
    private final boolean closeClientOnStop;
    private final boolean stopOnInformerErrorDuringStartup;
    private final int concurrentWorkflowExecutorThreads;
    private final Duration cacheSyncTimeout;
    @SuppressWarnings("rawtypes")
    private final Map<String, DependentResource> knownDependents = new ConcurrentHashMap<>();
    private final boolean useSSA;

    public QuarkusConfigurationService(
            Version version,
            Collection<QuarkusControllerConfiguration<?>> configurations,
            KubernetesClient kubernetesClient,
            CRDGenerationInfo crdInfo, int maxThreads, int maxWorflowThreads,
            int timeout, Duration cacheSyncTimeout, Metrics metrics, boolean startOperator,
            LeaderElectionConfiguration leaderElectionConfiguration, InformerStoppedHandler informerStoppedHandler,
            boolean closeClientOnStop, boolean stopOnInformerErrorDuringStartup,
            boolean useSSA) {
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
        this.terminationTimeout = timeout;
        this.cacheSyncTimeout = cacheSyncTimeout;
        this.informerStoppedHandler = informerStoppedHandler;
        this.leaderElectionConfiguration = leaderElectionConfiguration;
        this.useSSA = useSSA;
    }

    @Override
    public <R extends HasMetadata> QuarkusControllerConfiguration<R> getConfigurationFor(Reconciler<R> reconciler) {
        final var unwrapped = unwrap(reconciler);
        final var configuration = (QuarkusControllerConfiguration<R>) super.getConfigurationFor(unwrapped);
        configuration.initAnnotationConfigurables(unwrapped);
        return configuration;
    }

    @Override
    public boolean checkCRDAndValidateLocalModel() {
        return crdInfo.isValidateCRDs();
    }

    private static <R extends HasMetadata> Reconciler<R> unwrap(Reconciler<R> reconciler) {
        return ClientProxy.unwrap(reconciler);
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

    @Override
    public KubernetesClient getKubernetesClient() {
        return super.getKubernetesClient();
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

    @Override
    public DependentResourceFactory<QuarkusControllerConfiguration<?>> dependentResourceFactory() {
        return this;
    }

    @Override
    public ManagedWorkflowFactory<QuarkusControllerConfiguration<?>> getWorkflowFactory() {
        return this;
    }

    @Override
    public ManagedWorkflow<?> workflowFor(QuarkusControllerConfiguration<?> controllerConfiguration) {
        return controllerConfiguration.getWorkflow();
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public DependentResource createFrom(DependentResourceSpec spec, QuarkusControllerConfiguration configuration) {
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
                DependentResourceConfigurationResolver.configure(dependentResource, spec, configuration);
                // record the configured dependent for later retrieval if needed
                knownDependents.put(dependentKey, dependentResource);
            }
        }
        return dependentResource;
    }

    @SuppressWarnings("rawtypes")
    private static String getDependentKey(QuarkusControllerConfiguration configuration,
            DependentResourceSpec spec) {
        return getDependentKeyFromNames(configuration.getName(), spec.getName());
    }

    private static String getDependentKeyFromNames(String controllerName, String dependentName) {
        return controllerName + "#" + dependentName;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public DependentResourceSpecMetadata getDependentByName(String controllerName, String dependentName) {
        return (DependentResourceSpecMetadata) controllerConfigurations()
                .filter(cc -> controllerName.equals(cc.getName()))
                .findFirst()
                .flatMap(cc -> cc.getDependentResources().stream()
                        .filter(drs -> dependentName.equals(((DependentResourceSpec) drs).getName()))
                        .findFirst())
                .orElse(null);
    }

    @SuppressWarnings("rawtypes")
    public ManagedWorkflow workflowByName(String name) {
        return ((QuarkusControllerConfiguration) getFor(name)).getWorkflow();
    }

    @Override
    public boolean ssaBasedCreateUpdateMatchForDependentResources() {
        return useSSA;
    }
}
