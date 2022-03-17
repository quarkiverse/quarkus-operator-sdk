package io.halkyon;

import static io.halkyon.ExposedAppReconciler.LABELS_CONTEXT_KEY;
import static io.halkyon.ExposedAppReconciler.createMetadata;

import java.util.Map;

import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;

public class IngressDependent extends KubernetesDependentResource<Ingress, ExposedApp> implements
        Creator<Ingress, ExposedApp> {

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
}
