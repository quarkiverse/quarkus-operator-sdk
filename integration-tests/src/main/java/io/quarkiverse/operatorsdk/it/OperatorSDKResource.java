package io.quarkiverse.operatorsdk.it;

import java.util.Set;

import javax.enterprise.event.Event;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.RetryConfiguration;
import io.javaoperatorsdk.operator.api.config.Version;
import io.quarkiverse.operatorsdk.runtime.QuarkusControllerConfiguration;

@Path("/operator")
public class OperatorSDKResource {

    @Inject
    Instance<ResourceController<? extends CustomResource>> controllers;
    @Inject
    ConfigurationService configurationService;
    @Inject
    Event<DelayedController.RegisterEvent> event;

    @POST
    @Path("register")
    public void registerController() {
        event.fire(new DelayedController.RegisterEvent());
    }

    @GET
    @Path("registered/{name}")
    public boolean getRegisteredController(@PathParam("name") String name) {
        for (ResourceController<?> cont : controllers) {
            if (configurationService.getConfigurationFor(cont).getName().equals(name)
                    && cont instanceof RegistrableController) {
                return ((RegistrableController<?>) cont).isInitialized();
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
        return configurationService.getKnownControllerNames().contains(name);
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
        private final ConfigurationService conf;

        public JSONConfiguration(ConfigurationService conf) {
            this.conf = conf;
        }

        public Set<String> getKnownControllerNames() {
            return conf.getKnownControllerNames();
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
    }

    static class JSONControllerConfiguration {

        private final ControllerConfiguration conf;

        public JSONControllerConfiguration(ControllerConfiguration conf) {
            this.conf = conf;
        }

        public String getName() {
            return conf.getName();
        }

        @JsonProperty("crdName")
        public String getCRDName() {
            return conf.getCRDName();
        }

        public String getFinalizer() {
            return conf.getFinalizer();
        }

        public boolean isGenerationAware() {
            return conf.isGenerationAware();
        }

        public String getCustomResourceClass() {
            return conf.getCustomResourceClass().getCanonicalName();
        }

        public String getAssociatedControllerClassName() {
            return conf.getAssociatedControllerClassName();
        }

        public String[] getNamespaces() {
            return (String[]) conf.getNamespaces().toArray(new String[0]);
        }

        public boolean watchAllNamespaces() {
            return conf.watchAllNamespaces();
        }

        public RetryConfiguration getRetryConfiguration() {
            return conf.getRetryConfiguration();
        }

        public boolean isDelayed() {
            return conf instanceof QuarkusControllerConfiguration
                    && ((QuarkusControllerConfiguration) conf).isRegistrationDelayed();
        }
    }
}
