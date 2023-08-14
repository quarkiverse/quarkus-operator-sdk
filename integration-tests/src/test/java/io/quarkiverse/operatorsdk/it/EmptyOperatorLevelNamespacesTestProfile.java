package io.quarkiverse.operatorsdk.it;

import java.util.Collections;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class EmptyOperatorLevelNamespacesTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Collections.singletonMap("quarkus.operator-sdk.namespaces", "");
    }

}
