package io.quarkiverse.operatorsdk.runtime;

import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

@ConfigGroup
public class BuildTimeControllerConfiguration {

    /**
     * An optional list of comma-separated namespace names the controller should watch. If this
     * property is left empty then the controller will watch all namespaces.
     */
    @ConfigItem
    public Optional<List<String>> namespaces;

    /**
     * Whether the controller should only process events if the associated resource generation has
     * increased since last reconciliation, otherwise will process all events.
     */
    @ConfigItem(defaultValue = "true")
    public Optional<Boolean> generationAware;

    /**
     * The optional fully qualified name of a CDI event class that the controller will wait for before
     * registering with the Operator.
     */
    public Optional<String> delayRegistrationUntilEvent;

}
