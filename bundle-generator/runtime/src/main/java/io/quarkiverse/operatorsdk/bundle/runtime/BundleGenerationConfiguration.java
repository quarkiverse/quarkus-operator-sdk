package io.quarkiverse.operatorsdk.bundle.runtime;

import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

@ConfigRoot(name = "operator-sdk.bundle", phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public class BundleGenerationConfiguration {
    /**
     * Whether the extension should generate the Operator bundle.
     */
    @ConfigItem(defaultValue = "true")
    public Boolean enabled;

    /**
     * The list of channels that bundle belongs to. By default, it's "alpha".
     */
    @ConfigItem(defaultValue = "alpha")
    public List<String> channels;

    /**
     * The default channel for the bundle.
     */
    @ConfigItem
    public Optional<String> defaultChannel;

    /**
     * The name of the package that bundle belongs to.
     */
    @ConfigItem
    public Optional<String> packageName;

}
