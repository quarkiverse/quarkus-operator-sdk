package io.quarkiverse.operatorsdk.it.cdi;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.javaoperatorsdk.operator.monitoring.micrometer.MicrometerMetricsV2;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(DefaultMetricsTest.NoV1MetricsProfile.class)
class DefaultMetricsTest {

    @Inject
    Metrics metrics;

    @Test
    void shouldUseMicrometerV2MetricsByDefault() {
        assertInstanceOf(MicrometerMetricsV2.class, metrics,
                "Expected MicrometerMetricsV2 but got: " + metrics.getClass().getName()
                        + " — operator metrics will not be recorded");
    }

    public static class NoV1MetricsProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.operator-sdk.use-v1-metrics", "false");
        }
    }
}
