package io.quarkiverse.operatorsdk.runtime;

import jakarta.inject.Singleton;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.quarkus.arc.Arc;
import io.quarkus.jackson.ObjectMapperCustomizer;

@Singleton
public class KubernetesClientObjectMapperCustomizer implements
        io.quarkus.kubernetes.client.KubernetesClientObjectMapperCustomizer {

    @Override
    public void customize(ObjectMapper mapper) {
        // disable failure on empty beans
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

        // apply now deprecated user-defined customizations
        Arc.container()
                .select(ObjectMapperCustomizer.class, KubernetesClientSerializationCustomizer.Literal.INSTANCE)
                .stream()
                .sorted()
                .forEach(c -> c.customize(mapper));
    }
}
