package io.quarkiverse.operatorsdk.it;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusIntegrationTest
@TestProfile(SetOperatorLevelNamespacesTestProfile.class)
public class NativeSetOperatorLevelNamespacesIT extends SetOperatorLevelNamespacesTest {
}
