package io.quarkiverse.operatorsdk.common;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.api.config.Utils;

class ConfigurationUtilsTest {
    @Test
    void shouldUseSystemPropertyIfPresent() {
        if (Utils.isValidateCustomResourcesEnvVarSet()) {
            Assertions.assertEquals(Utils.shouldCheckCRDAndValidateLocalModel(),
                    ConfigurationUtils.shouldValidateCustomResources(false));
        }
    }
}
