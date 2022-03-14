package io.quarkiverse.operatorsdk.it;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;

public class ReadOnlyDependentResource extends KubernetesDependentResource<Deployment, ConfigMap> {
}
