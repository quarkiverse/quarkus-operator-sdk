package io.quarkiverse.operatorsdk.it;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.InformerConfig;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.quarkiverse.operatorsdk.it.CRUDDependentResource.TestOnAddFilter;

@KubernetesDependent(informerConfig = @InformerConfig(labelSelector = CRUDDependentResource.LABEL_SELECTOR, onAddFilter = TestOnAddFilter.class))
public class CRUDDependentResource extends CRUDKubernetesDependentResource<ConfigMap, ConfigMap> {

    public static class TestOnAddFilter implements OnAddFilter<ConfigMap> {

        @Override
        public boolean accept(ConfigMap configMap) {
            return true;
        }
    }

    public static final String LABEL_SELECTOR = "environment=production,foo=bar";

    public CRUDDependentResource() {
        super(ConfigMap.class);
    }
}
