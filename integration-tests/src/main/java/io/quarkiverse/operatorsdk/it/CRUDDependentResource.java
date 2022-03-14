package io.quarkiverse.operatorsdk.it;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CrudKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@KubernetesDependent(labelSelector = CRUDDependentResource.LABEL_SELECTOR)
public class CRUDDependentResource extends CrudKubernetesDependentResource<ConfigMap, ConfigMap> {

    public static final String LABEL_SELECTOR = "environment=production,foo=bar";
}
