package io.quarkiverse.operatorsdk.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkiverse.operatorsdk.runtime.QuarkusConfigurationService;
import io.quarkiverse.operatorsdk.test.sources.SimpleCR;
import io.quarkiverse.operatorsdk.test.sources.SimpleSpec;
import io.quarkiverse.operatorsdk.test.sources.SimpleStatus;
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
                .unmarshal("{\"status\":{\"mixin\": \"fromMixin\"}}", SimpleCR.class);
        assertEquals("fromMixin", result.getStatus().value);

        serialization = service.getKubernetesClient().getKubernetesSerialization();
        result = serialization
                .unmarshal("{\"status\":{\"mixin\": \"fromMixin\"}}", SimpleCR.class);
        assertEquals("fromMixin", result.getStatus().value);
    }

    @RegisterExtension
    static QuarkusUnitTest runner = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(Customizer.class, ValueMixIn.class, SimpleCR.class,
                    SimpleSpec.class, SimpleStatus.class));

    private static final class ValueMixIn {
        @JsonProperty("mixin")
        String value;
    }

    @Singleton
    public static class Customizer implements KubernetesClientObjectMapperCustomizer {
        public void customize(ObjectMapper objectMapper) {
            objectMapper.addMixIn(SimpleStatus.class, ValueMixIn.class);
        }
    }
}
