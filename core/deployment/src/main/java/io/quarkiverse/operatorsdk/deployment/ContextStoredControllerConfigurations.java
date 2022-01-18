package io.quarkiverse.operatorsdk.deployment;

import java.util.HashMap;
import java.util.Map;

import io.quarkiverse.operatorsdk.runtime.QuarkusControllerConfiguration;

@SuppressWarnings("rawtypes")
public class ContextStoredControllerConfigurations {
    private final Map<String, QuarkusControllerConfiguration> configurations = new HashMap<>();

    public Map<String, QuarkusControllerConfiguration> getConfigurations() {
        return configurations;
    }
}
