package io.quarkiverse.operatorsdk.runtime;

import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

@ConfigGroup
public class RunTimeControllerConfiguration {

    /**
     * An optional list of comma-separated namespace names the controller should watch. If this
     * property is left empty then the controller will watch all namespaces.
     */
    @ConfigItem
    public Optional<List<String>> namespaces;

    /**
     * The optional name of the finalizer for the controller. If none is provided, one will be
     * automatically generated.
     */
    @ConfigItem
    public Optional<String> finalizer;

    /**
     * The optional controller retry configuration
     */
    @ConfigItem
    public Optional<ExternalRetryConfiguration> retry;
}
