package io.quarkiverse.operatorsdk.deployment.helm;

import java.util.function.BooleanSupplier;

import io.quarkiverse.operatorsdk.runtime.BuildTimeOperatorConfiguration;

class HelmGenerationEnabled implements BooleanSupplier {
    private BuildTimeOperatorConfiguration config;

    @Override
    public boolean getAsBoolean() {
        return config.helm.enabled;
    }
}
