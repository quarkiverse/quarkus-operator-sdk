package io.halkyon;

import org.junit.jupiter.api.Disabled;

import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
@Disabled("Currently not possible to inject the dev service-provided k8s client in native app")
public class NativeExposedAppReconcilerIT extends ExposedAppReconcilerTest {

}
