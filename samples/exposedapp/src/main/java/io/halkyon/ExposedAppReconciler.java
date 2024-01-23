package io.halkyon;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_CURRENT_NAMESPACE;

import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.quarkiverse.operatorsdk.annotations.CSVMetadata;

@ControllerConfiguration(namespaces = WATCH_CURRENT_NAMESPACE, name = "exposedapp", dependents = {
        @Dependent(type = DeploymentDependent.class),
        @Dependent(name = "service", type = ServiceDependent.class),
        @Dependent(type = IngressDependent.class, readyPostcondition = IngressDependent.class)
})
@CSVMetadata(displayName = "ExposedApp operator", description = "A sample operator that shows how to use JOSDK's main features with the Quarkus extension")
public class ExposedAppReconciler implements Reconciler<ExposedApp>,
        ContextInitializer<ExposedApp> {

    static final Logger log = LoggerFactory.getLogger(ExposedAppReconciler.class);
    static final String APP_LABEL = "app.kubernetes.io/name";
    static final String LABELS_CONTEXT_KEY = "labels";

    public ExposedAppReconciler() {
    }

    @Override
    public void initContext(ExposedApp exposedApp, Context context) {
        final var labels = Map.of(APP_LABEL, exposedApp.getMetadata().getName());
        context.managedDependentResourceContext().put(LABELS_CONTEXT_KEY, labels);
    }

    @Override
    public UpdateControl<ExposedApp> reconcile(ExposedApp exposedApp, Context<ExposedApp> context) {
        final var name = exposedApp.getMetadata().getName();
        // retrieve the workflow reconciliation result and re-schedule if we have dependents that are not yet ready
        final var wrs = context.managedDependentResourceContext().getWorkflowReconcileResult();
        if (wrs.allDependentResourcesReady()) {

            final var url = IngressDependent.getExposedURL(
                    context.getSecondaryResource(Ingress.class).orElseThrow());
            exposedApp.setStatus(new ExposedAppStatus(url, exposedApp.getSpec().getEndpoint()));
            log.info("App {} is exposed and ready to be used at {}", name, exposedApp.getStatus().getHost());
            return UpdateControl.updateStatus(exposedApp);
        } else {
            final var duration = Duration.ofSeconds(1);
            log.info("App {} is not ready yet, rescheduling reconciliation after {}s", name, duration.toSeconds());
            return UpdateControl.<ExposedApp> noUpdate().rescheduleAfter(duration);
        }
    }

    static ObjectMeta createMetadata(ExposedApp resource, Map<String, String> labels) {
        final var metadata = resource.getMetadata();
        return new ObjectMetaBuilder()
                .withName(metadata.getName())
                .withNamespace(metadata.getNamespace())
                .withLabels(labels)
                .build();
    }
}
