package io.quarkiverse.operatorsdk.bundle.deployment.builders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;

class ManifestsBuilderTest {

    @Test
    void preservesFabric8ManifestSerialization() {
        final var deployment = new DeploymentBuilder()
                .withNewMetadata().withName("operator").endMetadata()
                .withNewSpec().withReplicas(1).endSpec()
                .build();

        final var yaml = ManifestsBuilder.YAML_MAPPER.writeValueAsString(deployment);
        final var result = Serialization.unmarshal(yaml, Deployment.class);

        assertFalse(yaml.startsWith("---"));
        assertEquals(deployment, result);
    }

    @Test
    void omitsEmptyValuesAndPreservesNumericStrings() {
        final var yaml = ManifestsBuilder.YAML_MAPPER.writeValueAsString(
                Map.of("version", "123", "empty", List.of()));
        final var result = ManifestsBuilder.YAML_MAPPER.readTree(yaml);

        assertEquals("123", result.get("version").asString());
        assertFalse(result.has("empty"));
    }
}
