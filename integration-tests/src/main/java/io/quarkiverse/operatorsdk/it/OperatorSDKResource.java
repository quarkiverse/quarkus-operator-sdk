package io.quarkiverse.operatorsdk.it;

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
import io.quarkiverse.operatorsdk.it.TestController.RegisterEvent;

@Path("/operator")
public class OperatorSDKResource {

    @Inject
    Instance<ResourceController<? extends CustomResource>> controllers;
    @Inject
    ConfigurationService configurationService;
    @Inject
    Event<RegisterEvent> event;

    @POST
    @Path("register")
    public void registerController() {
        event.fire(new TestController.RegisterEvent());
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
    @Path("validateCR")
    public boolean validateCR() {
        return configurationService.checkCRDAndValidateLocalModel();
    }

    @GET
    @Path("maxThreads")
    public int maxThreads() {
        return configurationService.concurrentReconciliationThreads();
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
    }
}
