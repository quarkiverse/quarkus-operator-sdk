package io.quarkiverse.operatorsdk.it;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.cache.ItemStore;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.Version;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.config.workflow.WorkflowSpec;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig;
import io.javaoperatorsdk.operator.processing.dependent.workflow.ManagedWorkflow;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimiter;
import io.javaoperatorsdk.operator.processing.retry.Retry;
import io.quarkiverse.operatorsdk.runtime.DependentResourceSpecMetadata;
import io.quarkiverse.operatorsdk.runtime.QuarkusConfigurationService;
import io.quarkiverse.operatorsdk.runtime.QuarkusControllerConfiguration;
import io.quarkus.runtime.annotations.RegisterForReflection;

@SuppressWarnings("unused")
@Path("/operator")
public class OperatorSDKResource {

    @Inject
    Instance<Reconciler<? extends HasMetadata>> controllers;
    @Inject
    QuarkusConfigurationService configurationService;

    @GET
    @Path("config")
    public JSONConfiguration config() {
        return new JSONConfiguration(configurationService);
    }

    @GET
    @Path("{name}")
    public boolean exists(@PathParam("name") String name) {
        return configurationService.getKnownReconcilerNames().contains(name);
    }

    @GET
    @Path("controllers")
    @Produces("application/json")
    public Set<String> getControllerNames() {
        return config().getKnownControllerNames();
    }

    @GET
    @Path("{name}/config")
    public JSONControllerConfiguration getConfig(@PathParam("name") String name) {
        return getControllerConfigurationByName(name)
                .map(JSONControllerConfiguration::new)
                .orElse(null);
    }

    private Optional<? extends QuarkusControllerConfiguration<? extends HasMetadata>> getControllerConfigurationByName(
            String name) {
        return controllers.stream()
                .map(c -> configurationService.getConfigurationFor(c))
                .filter(c -> c.getName().equals(name))
                .findFirst();
    }

    @GET
    @Path("{name}/workflow")
    public JSONWorkflow getWorkflow(@PathParam("name") String name) {
        return new JSONWorkflow(configurationService.workflowByName(name));
    }

    @GET
    @Path("{name}/dependents/{dependent}")
    public JSONKubernetesResourceConfig getDependentConfig(@PathParam("name") String name,
            @PathParam("dependent") String dependent) {
        final DependentResourceSpecMetadata<?, ?, ?> dr = configurationService.getDependentByName(name, dependent);
        if (dr == null) {
            return null;
        }
        return dr.getConfiguration()
                .filter(KubernetesDependentResourceConfig.class::isInstance)
                .map(KubernetesDependentResourceConfig.class::cast)
                .map(JSONKubernetesResourceConfig::new)
                .orElse(null);
    }

    static class JSONConfiguration {
        private final QuarkusConfigurationService conf;

        public JSONConfiguration(QuarkusConfigurationService conf) {
            this.conf = conf;
        }

        public Set<String> getKnownControllerNames() {
            return conf.getKnownReconcilerNames();
        }

        public Version getVersion() {
            return conf.getVersion();
        }

        @JsonProperty("validate")
        public boolean validate() {
            return conf.checkCRDAndValidateLocalModel();
        }

        @JsonProperty("maxThreads")
        public int concurrentReconciliationThreads() {
            return conf.concurrentReconciliationThreads();
        }

        @JsonProperty("timeout")
        public int timeout() {
            return conf.getTerminationTimeoutSeconds();
        }

        @JsonProperty("applyCRDs")
        public boolean apply() {
            return conf.getCRDGenerationInfo().isApplyCRDs();
        }

        @JsonProperty("metrics")
        public String metrics() {
            return conf.getMetrics().getClass().getName();
        }

        @JsonProperty("registryBound")
        public boolean registryBound() {
            final var metrics = conf.getMetrics();
            return metrics instanceof TestMetrics && ((TestMetrics) metrics).isRegistryBound();
        }

        @JsonProperty("leaderConfig")
        public String leaderConfig() {
            return conf.getLeaderElectionConfiguration().map(lec -> lec.getClass().getName()).orElse(null);
        }

        @JsonProperty("useSSA")
        public boolean useSSA() {
            return conf.ssaBasedCreateUpdateMatchForDependentResources();
        }
    }

    static class JSONControllerConfiguration {

        private final ControllerConfiguration<?> conf;

        public JSONControllerConfiguration(ControllerConfiguration<?> conf) {
            this.conf = conf;
        }

        public String getName() {
            return conf.getName();
        }

        @JsonProperty("crdName")
        public String getCRDName() {
            return conf.getResourceTypeName();
        }

        public String getFinalizer() {
            return conf.getFinalizerName();
        }

        public boolean isGenerationAware() {
            return conf.isGenerationAware();
        }

        public String getCustomResourceClass() {
            return conf.getResourceClass().getCanonicalName();
        }

        public String getAssociatedControllerClassName() {
            return conf.getAssociatedReconcilerClassName();
        }

        public String[] getNamespaces() {
            return conf.getInformerConfig().getNamespaces().toArray(new String[0]);
        }

        @JsonProperty("watchAllNamespaces")
        public boolean watchAllNamespaces() {
            return conf.getInformerConfig().watchAllNamespaces();
        }

        @JsonProperty("watchCurrentNamespace")
        public boolean watchCurrentNamespace() {
            return conf.getInformerConfig().watchCurrentNamespace();
        }

        public Retry getRetry() {
            return conf.getRetry();
        }

        public String getLabelSelector() {
            return conf.getInformerConfig().getLabelSelector();
        }

        public List<JSONDependentResourceSpec> getDependents() {
            final var dependents = conf.getWorkflowSpec().map(WorkflowSpec::getDependentResourceSpecs)
                    .orElse(Collections.emptyList());
            final var result = new ArrayList<JSONDependentResourceSpec>(dependents.size());
            return dependents.stream()
                    .map(JSONDependentResourceSpec::new)
                    .collect(Collectors.toList());
        }

        @JsonProperty("maxReconciliationIntervalSeconds")
        public long maxReconciliationIntervalSeconds() {
            return conf.maxReconciliationInterval().map(Duration::getSeconds).orElseThrow();
        }

        @SuppressWarnings("rawtypes")
        public RateLimiter getRateLimiter() {
            return conf.getRateLimiter();
        }

        public ItemStore<?> getItemStore() {
            return conf.getInformerConfig().getItemStore();
        }
    }

    static class JSONDependentResourceSpec {
        private final DependentResourceSpec<?, ?, ?> spec;

        JSONDependentResourceSpec(DependentResourceSpec<?, ?, ?> spec) {
            this.spec = spec;
        }

        public String getDependentClass() {
            return spec.getDependentResourceClass().getCanonicalName();
        }

        public Object getDependentConfig() {
            final var c = spec.getConfiguration().orElse(null);
            if (c instanceof KubernetesDependentResourceConfig) {
                return new JSONKubernetesResourceConfig((KubernetesDependentResourceConfig<?>) c);
            } else {
                return c;
            }
        }

        public String getName() {
            return spec.getName();
        }
    }

    // needed for native tests, see https://quarkus.io/guides/writing-native-applications-tips#registering-for-reflection
    @RegisterForReflection
    static class JSONKubernetesResourceConfig {

        private final KubernetesDependentResourceConfig<?> config;

        JSONKubernetesResourceConfig(KubernetesDependentResourceConfig<?> config) {
            this.config = config;
        }

        public String getOnAddFilter() {
            return Optional.ofNullable(config.informerConfig().getOnAddFilter())
                    .map(f -> f.getClass().getCanonicalName())
                    .orElse(null);
        }

        public String getLabelSelector() {
            return config.informerConfig().getLabelSelector();
        }
    }

    static class JSONWorkflow {
        private final ManagedWorkflow<?> workflow;

        @SuppressWarnings("rawtypes")
        JSONWorkflow(ManagedWorkflow workflow) {
            this.workflow = workflow;
        }

        public boolean isCleaner() {
            return workflow.hasCleaner();
        }

        public boolean isEmpty() {
            return workflow.isEmpty();
        }

        public Map<String, JSONDRSpec> getDependents() {
            return workflow.getOrderedSpecs().stream()
                    .collect(Collectors.toMap(DependentResourceSpec::getName, JSONDRSpec::new));
        }
    }

    @SuppressWarnings("rawtypes")
    static class JSONDRSpec {
        private final DependentResourceSpec spec;

        JSONDRSpec(DependentResourceSpec spec) {
            this.spec = spec;
        }

        public String getType() {
            return spec.getDependentResourceClass().getName();
        }

        public String getReadyCondition() {
            final var readyCondition = spec.getReadyCondition();
            return readyCondition == null ? null : readyCondition.getClass().getName();
        }
    }
}
