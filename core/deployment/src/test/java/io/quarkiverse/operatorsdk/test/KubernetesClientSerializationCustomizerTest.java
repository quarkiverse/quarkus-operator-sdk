package io.quarkiverse.operatorsdk.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkiverse.operatorsdk.runtime.KubernetesClientSerializationCustomizer;
import io.quarkiverse.operatorsdk.runtime.QuarkusConfigurationService;
import io.quarkiverse.operatorsdk.test.sources.SimpleCR;
import io.quarkiverse.operatorsdk.test.sources.SimpleSpec;
import io.quarkiverse.operatorsdk.test.sources.SimpleStatus;
import io.quarkus.jackson.ObjectMapperCustomizer;
import io.quarkus.kubernetes.client.KubernetesClientObjectMapperCustomizer;
import io.quarkus.test.QuarkusUnitTest;

public class KubernetesClientSerializationCustomizerTest {

    @Inject
    KubernetesClient kubernetesClient;
    @Inject
    QuarkusConfigurationService service;

    @Test
    public void kubernetesClientUsesCustomizedObjectMapper() {
        assertEquals(service.getKubernetesClient(), kubernetesClient);
        var serialization = kubernetesClient.getKubernetesSerialization();
        var result = serialization
                .unmarshal("{\"spec\": {\"mixin\": \"fromMixin\"},\"status\":{\"mixin\": \"fromMixin\"}}", SimpleCR.class);
        assertEquals("fromMixin", result.getSpec().value);
        assertEquals("fromMixin", result.getStatus().value);

        serialization = service.getKubernetesClient().getKubernetesSerialization();
        result = serialization
                .unmarshal("{\"spec\": {\"mixin\": \"fromMixin\"},\"status\":{\"mixin\": \"fromMixin\"}}", SimpleCR.class);
        assertEquals("fromMixin", result.getSpec().value);
        assertEquals("fromMixin", result.getStatus().value);
    }

    @RegisterExtension
    static QuarkusUnitTest runner = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(Customizer.class, Customizer2.class, ValueMixIn.class, SimpleCR.class,
                    SimpleSpec.class, SimpleStatus.class));

    @Singleton
    @KubernetesClientSerializationCustomizer
    public static class Customizer implements ObjectMapperCustomizer {
        @Override
        public void customize(ObjectMapper objectMapper) {
            objectMapper.addMixIn(SimpleSpec.class, ValueMixIn.class);
        }
    }

    private static final class ValueMixIn {
        @JsonProperty("mixin")
        String value;
    }

    @Singleton
    public static class Customizer2 implements KubernetesClientObjectMapperCustomizer {
        public void customize(ObjectMapper objectMapper) {
            objectMapper.addMixIn(SimpleStatus.class, ValueMixIn.class);
        }
    }
}
