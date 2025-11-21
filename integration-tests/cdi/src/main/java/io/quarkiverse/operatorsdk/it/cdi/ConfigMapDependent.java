package io.quarkiverse.operatorsdk.it.cdi;

import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public class ConfigMapDependent extends CRUDKubernetesDependentResource<ConfigMap, TestResource> {

    private static final Logger log = LoggerFactory.getLogger(ConfigMapDependent.class);

    @Override
    protected ConfigMap desired(TestResource primary, Context<TestResource> context) {
        Optional<ConfigMap> optionalConfigMap = getConfigMap(primary, context);
        if (!optionalConfigMap.isPresent()) {
            ConfigMap configMap = new ConfigMap();
            configMap.setMetadata(
                    new ObjectMetaBuilder()
                            .withName(primary.getMetadata().getName() + "-cm")
                            .withNamespace(primary.getMetadata().getNamespace())
                            .build());
            configMap.setData(Map.of("key", "data"));
            return configMap;
        }
        return optionalConfigMap.get();
    }

    @Override
    public void delete(TestResource primary, Context<TestResource> context) {
        Optional<ConfigMap> optionalConfigMap = getConfigMap(primary, context);
        optionalConfigMap.ifPresent(
                (configMap -> {
                    if (configMap.getMetadata().getAnnotations() != null) {
                        context.getClient().resource(configMap).delete();
                    }
                }));
    }

    private static Optional<ConfigMap> getConfigMap(TestResource primary, Context<TestResource> context) {
        Optional<ConfigMap> optionalConfigMap = context.getSecondaryResource(ConfigMap.class);
        if (optionalConfigMap.isEmpty()) {
            log.debug("Config Map not found for primary: {}", ResourceID.fromResource(primary));
            return Optional.empty();
        }
        return optionalConfigMap;
    }
}
