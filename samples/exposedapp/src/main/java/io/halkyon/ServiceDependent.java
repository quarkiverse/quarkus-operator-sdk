package io.halkyon;

import static io.halkyon.ExposedAppReconciler.LABELS_CONTEXT_KEY;
import static io.halkyon.ExposedAppReconciler.createMetadata;

import java.util.Map;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;

public class ServiceDependent extends CRUDKubernetesDependentResource<Service, ExposedApp> {

    public ServiceDependent() {
        super(Service.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Service desired(ExposedApp exposedApp, Context context) {
        final var labels = (Map<String, String>) context.managedDependentResourceContext()
                .getMandatory(LABELS_CONTEXT_KEY, Map.class);

        return new ServiceBuilder()
                .withMetadata(createMetadata(exposedApp, labels))
                .withNewSpec()
                .addNewPort()
                .withName("http")
                .withPort(8080)
                //            .withNewTargetPort().withValue(8080).endTargetPort() // todo: fabric8 6
                .withNewTargetPort().withIntVal(8080).endTargetPort()
                .endPort()
                .withSelector(labels)
                .withType("ClusterIP")
                .endSpec()
                .build();
    }
}
