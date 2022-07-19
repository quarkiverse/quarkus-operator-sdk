package io.quarkiverse.operatorsdk.it;

import java.util.Collections;
import java.util.Set;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;

public class ReadOnlyDependentResource extends KubernetesDependentResource<Deployment, ConfigMap> implements
        SecondaryToPrimaryMapper<Deployment> {

    public ReadOnlyDependentResource() {
        super(Deployment.class);
    }

    @Override
    public Set<ResourceID> toPrimaryResourceIDs(Deployment deployment) {
        return Collections.emptySet();
    }
}
