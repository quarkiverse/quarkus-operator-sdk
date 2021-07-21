package io.quarkiverse.operatorsdk.deployment;

import java.util.HashMap;
import java.util.Map;

import io.quarkiverse.operatorsdk.runtime.QuarkusControllerConfiguration;

public class ContextStoredControllerConfigurations {
    private Map<String, QuarkusControllerConfiguration> configurations = new HashMap<>();

    public Map<String, QuarkusControllerConfiguration> getConfigurations() {
        return configurations;
    }
}
