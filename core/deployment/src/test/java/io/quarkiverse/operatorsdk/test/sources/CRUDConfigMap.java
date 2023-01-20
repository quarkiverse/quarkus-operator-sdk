package io.quarkiverse.operatorsdk.test.sources;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;

public class CRUDConfigMap extends CRUDKubernetesDependentResource<ConfigMap, TestCR> {

    public CRUDConfigMap() {
        super(ConfigMap.class);
    }
}
