package io.quarkiverse.operatorsdk.it;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.RetryConfiguration;
import io.javaoperatorsdk.operator.api.config.Version;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig;
import io.javaoperatorsdk.operator.processing.retry.Retry;
import io.quarkiverse.operatorsdk.runtime.QuarkusConfigurationService;
import io.quarkiverse.operatorsdk.runtime.QuarkusKubernetesDependentResourceConfig;
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
    public boolean getController(@PathParam("name") String name) {
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
        return controllers.stream()
                .map(c -> configurationService.getConfigurationFor(c))
                .filter(c -> c.getName().equals(name))
                .findFirst()
                .map(JSONControllerConfiguration::new)
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
            return conf.getNamespaces().toArray(new String[0]);
        }

        @JsonProperty("watchAllNamespaces")
        public boolean watchAllNamespaces() {
            return conf.watchAllNamespaces();
        }

        @JsonProperty("watchCurrentNamespace")
        public boolean watchCurrentNamespace() {
            return conf.watchCurrentNamespace();
        }

        public RetryConfiguration getRetryConfiguration() {
            return conf.getRetryConfiguration();
        }

        public Retry getRetry() {
            return conf.getRetry();
        }

        public String getLabelSelector() {
            return conf.getLabelSelector();
        }

        public List<JSONDependentResourceSpec> getDependents() {
            final var dependents = conf.getDependentResources();
            final var result = new ArrayList<JSONDependentResourceSpec>(dependents.size());
            return dependents.stream()
                    .map(JSONDependentResourceSpec::new)
                    .collect(Collectors.toList());
        }

        @JsonProperty("maxReconciliationIntervalSeconds")
        public long maxReconciliationIntervalSeconds() {
            return conf.maxReconciliationInterval().map(Duration::getSeconds).orElseThrow();
        }
    }

    // needed for native tests, see https://quarkus.io/guides/writing-native-applications-tips#registering-for-reflection
    @RegisterForReflection(targets = QuarkusKubernetesDependentResourceConfig.class)
    static class JSONDependentResourceSpec {
        private final DependentResourceSpec<?, ?> spec;

        JSONDependentResourceSpec(DependentResourceSpec<?, ?> spec) {
            this.spec = spec;
        }

        public String getDependentClass() {
            return spec.getDependentResourceClass().getCanonicalName();
        }

        public Object getDependentConfig() {
            return spec.getDependentResourceConfiguration()
                    .map(KubernetesDependentResourceConfig.class::cast)
                    .map(JSONKubernetesResourceConfig::new)
                    .orElse(null);
        }

        public String getName() {
            return spec.getName();
        }
    }

    static class JSONKubernetesResourceConfig {

        private final KubernetesDependentResourceConfig<?> config;

        JSONKubernetesResourceConfig(KubernetesDependentResourceConfig<?> config) {
            this.config = config;
        }

        public String getOnAddFilter() {
            return config.onAddFilter().getClass().getCanonicalName();
        }

        public String getLabelSelector() {
            return config.labelSelector();
        }
    }
}
