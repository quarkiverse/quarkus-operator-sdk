package io.quarkiverse.operatorsdk.bundle.runtime;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.operator-sdk.bundle")
@ConfigRoot
public interface BundleGenerationConfiguration {
    String DEFAULT_BUNDLE_NAME = "QOSDK_DEFAULT";

    /**
     * Whether the extension should generate the Operator bundle.
     */
    @WithDefault("true")
    Boolean enabled();

    /**
     * The list of channels that bundle belongs to. By default, it's "alpha".
     */
    @WithDefault("alpha")
    List<String> channels();

    /**
     * The default channel for the bundle.
     */
    Optional<String> defaultChannel();

    /**
     * The replaces value that should be used in the generated CSV.
     */
    Optional<String> replaces();

    /**
     * The version value that should be used in the generated CSV instead of the automatically detected one extracted from the
     * project information.
     */
    Optional<String> version();

    /**
     * Per-bundle configuration. Note that you can also provide default values that will be applied to all your bundles by
     * adding configuration using the {@link #DEFAULT_BUNDLE_NAME} key. In that case, any configuration found under that key
     * will be used as default for every bundle unless otherwise overridden.
     *
     * @since 6.8.0
     */
    Map<String, BundleConfiguration> bundles();
}
