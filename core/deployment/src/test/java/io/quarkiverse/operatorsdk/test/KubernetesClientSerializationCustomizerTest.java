package io.quarkiverse.operatorsdk.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkiverse.operatorsdk.runtime.KubernetesClientSerializationCustomizer;
import io.quarkus.jackson.ObjectMapperCustomizer;
import io.quarkus.test.QuarkusUnitTest;

@Disabled("Until https://github.com/quarkusio/quarkus/pull/34259 is backported to 3.2")
public class KubernetesClientSerializationCustomizerTest {

    @Inject
    KubernetesClient kubernetesClient;

    @Test
    public void kubernetesClientUsesCustomizedObjectMapper() {
        final var result = kubernetesClient.getKubernetesSerialization()
                .unmarshal("{\"quarkusName\":\"the-name\"}", ObjectMeta.class);
        assertEquals("the-name", result.getName());
    }

    @RegisterExtension
    static QuarkusUnitTest runner = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(Customizer.class));

    @Singleton
    @KubernetesClientSerializationCustomizer
    public static class Customizer implements ObjectMapperCustomizer {
        @Override
        public void customize(ObjectMapper objectMapper) {
            objectMapper.addMixIn(ObjectMeta.class, ObjectMetaMixin.class);
        }

        private static final class ObjectMetaMixin {
            @JsonProperty("quarkusName")
            String name;
        }
    }
}
