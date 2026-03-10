package io.quarkiverse.operatorsdk.runtime.api;

import java.util.function.Consumer;

import io.javaoperatorsdk.operator.api.config.ConfigurationServiceOverrider;

public interface ConfigurationServiceCustomizer {
    Consumer<ConfigurationServiceOverrider> overrider();
}
