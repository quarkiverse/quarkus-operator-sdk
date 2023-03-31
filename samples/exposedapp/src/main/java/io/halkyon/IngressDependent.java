package io.halkyon;

import static io.halkyon.ExposedAppReconciler.LABELS_CONTEXT_KEY;
import static io.halkyon.ExposedAppReconciler.createMetadata;

import java.util.Map;

import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

public class IngressDependent extends CRUDKubernetesDependentResource<Ingress, ExposedApp> implements
        Condition<Ingress, ExposedApp> {

    public IngressDependent() {
        super(Ingress.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Ingress desired(ExposedApp exposedApp, Context context) {
        final var labels = (Map<String, String>) context.managedDependentResourceContext()
                .getMandatory(LABELS_CONTEXT_KEY, Map.class);
        final var metadata = createMetadata(exposedApp, labels);
        metadata.setAnnotations(Map.of(
                "nginx.ingress.kubernetes.io/rewrite-target", "/",
                "kubernetes.io/ingress.class", "nginx"));

        return new IngressBuilder()
                .withMetadata(metadata)
                .withNewSpec()
                .addNewRule()
                .withNewHttp()
                .addNewPath()
                .withPath("/")
                .withPathType("Prefix")
                .withNewBackend()
                .withNewService()
                .withName(metadata.getName())
                .withNewPort().withNumber(8080).endPort()
                .endService()
                .endBackend()
                .endPath()
                .endHttp()
                .endRule()
                .endSpec()
                .build();
    }

    /**
     * Assumes Ingress is ready as determined by {@link #isMet(DependentResource, ExposedApp, Context)}
     *
     * @param ingress the ingress
     * @return the URL exposed by the specified Ingress
     */
    static String getExposedURL(Ingress ingress) {
        final var status = ingress.getStatus();
        final var ingresses = status.getLoadBalancer().getIngress();
        var ing = ingresses.get(0);
        String hostname = ing.getHostname();
        return "https://" + (hostname != null ? hostname : ing.getIp());
    }

    @Override
    public boolean isMet(DependentResource<Ingress, ExposedApp> dependentResource,
            ExposedApp exposedApp, Context<ExposedApp> context) {
        return context.getSecondaryResource(Ingress.class).map(in -> {
            final var status = in.getStatus();
            if (status != null) {
                final var ingresses = status.getLoadBalancer().getIngress();
                // only set the status if the ingress is ready to provide the info we need
                return ingresses != null && !ingresses.isEmpty();
            }
            return false;
        }).orElse(false);
    }
}
