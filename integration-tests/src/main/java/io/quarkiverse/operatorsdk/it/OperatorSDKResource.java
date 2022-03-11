package io.quarkiverse.operatorsdk.it;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.enterprise.event.Event;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
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
import io.quarkiverse.operatorsdk.runtime.QuarkusConfigurationService;
import io.quarkiverse.operatorsdk.runtime.QuarkusControllerConfiguration;

@Path("/operator")
public class OperatorSDKResource {

    @Inject
    Instance<Reconciler<? extends HasMetadata>> controllers;
    @Inject
    QuarkusConfigurationService configurationService;
    @Inject
    Event<DelayedReconciler.RegisterEvent> event;

    @POST
    @Path("register")
    public void registerController() {
        event.fire(new DelayedReconciler.RegisterEvent());
    }

    @GET
    @Path("registered/{name}")
    public boolean getRegisteredController(@PathParam("name") String name) {
        for (Reconciler<?> cont : controllers) {
            if (configurationService.getConfigurationFor(cont).getName().equals(name)
                    && cont instanceof RegistrableReconciler) {
                return ((RegistrableReconciler<?>) cont).isInitialized();
            }
        }
        throw new NotFoundException("Could not find controller: " + name);
    }

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
        final var configuration = controllers.stream()
                .map(c -> configurationService.getConfigurationFor(c))
                .filter(c -> c.getName().equals(name))
                .findFirst()
                .map(JSONControllerConfiguration::new)
                .orElse(null);
        return configuration;
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
            return conf.getFinalizer();
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

        public boolean isDelayed() {
            return conf instanceof QuarkusControllerConfiguration
                    && ((QuarkusControllerConfiguration<?>) conf).isRegistrationDelayed();
        }

        @JsonProperty("useFinalizer")
        public boolean useFinalizer() {
            return conf.useFinalizer();
        }

        public String getLabelSelector() {
            return conf.getLabelSelector();
        }

        public List<JSONDependentResourceSpec> getDependents() {
            return conf.getDependentResources().stream()
                    .map(JSONDependentResourceSpec::new)
                    .collect(Collectors.toList());
        }
    }

    static class JSONDependentResourceSpec {
        private final DependentResourceSpec<?, ?> spec;

        JSONDependentResourceSpec(DependentResourceSpec<?, ?> spec) {
            this.spec = spec;
        }

        public String getDependentClass() {
            return spec.getDependentResourceClass().getCanonicalName();
        }

        public Object getDependentConfig() {
            return spec.getDependentResourceConfiguration().orElse(null);
        }
    }

    static class JSONKubernetesDependentConfig extends KubernetesDependentResourceConfig {

    }
}
