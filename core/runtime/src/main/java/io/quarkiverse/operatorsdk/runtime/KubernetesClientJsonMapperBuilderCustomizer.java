package io.quarkiverse.operatorsdk.runtime;

import jakarta.inject.Singleton;

import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Singleton
public class KubernetesClientJsonMapperBuilderCustomizer implements
        io.quarkus.kubernetes.client.KubernetesClientJsonMapperBuilderCustomizer {

    @Override
    public void customize(JsonMapper.Builder builder) {
        // disable failure on empty beans
        builder.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }
}
