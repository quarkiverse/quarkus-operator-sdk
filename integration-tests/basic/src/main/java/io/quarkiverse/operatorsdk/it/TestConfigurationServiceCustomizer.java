package io.quarkiverse.operatorsdk.it;

import java.util.Set;
import java.util.function.Consumer;

import jakarta.inject.Singleton;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceOverrider;
import io.quarkiverse.operatorsdk.runtime.api.ConfigurationServiceCustomizer;

@Singleton
public class TestConfigurationServiceCustomizer implements ConfigurationServiceCustomizer {
    @Override
    public Consumer<ConfigurationServiceOverrider> overrider() {
        return overrider -> overrider.withDefaultNonSSAResource(Set.of(Pod.class, Secret.class, Deployment.class));
    }
}
