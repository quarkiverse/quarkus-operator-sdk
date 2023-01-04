package io.quarkiverse.operatorsdk.it;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.quarkiverse.operatorsdk.it.CRUDDependentResource.TestOnAddFilter;
import io.quarkiverse.operatorsdk.it.CRUDDependentResource.TestResourceDiscriminator;

@KubernetesDependent(labelSelector = CRUDDependentResource.LABEL_SELECTOR, onAddFilter = TestOnAddFilter.class, resourceDiscriminator = TestResourceDiscriminator.class)
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

    public static class TestResourceDiscriminator implements ResourceDiscriminator<ConfigMap, ConfigMap> {

        @Override
        public Optional<ConfigMap> distinguish(Class<ConfigMap> aClass, ConfigMap configMap,
                Context<ConfigMap> context) {
            return Optional.empty();
        }
    }
}
