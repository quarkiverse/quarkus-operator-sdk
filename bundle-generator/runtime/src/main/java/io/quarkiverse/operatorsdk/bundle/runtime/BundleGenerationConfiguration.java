package io.quarkiverse.operatorsdk.bundle.runtime;

import java.util.List;
import java.util.Optional;

import io.quarkiverse.operatorsdk.annotations.CSVMetadata;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.operator-sdk.bundle")
@ConfigRoot
public interface BundleGenerationConfiguration {
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
     * The name of the package that bundle belongs to.
     *
     * @deprecated Use {@link CSVMetadata#bundleName()} instead
     */
    @Deprecated(forRemoval = true)
    Optional<String> packageName();

    /**
     * The replaces value that should be used in the generated CSV.
     */
    Optional<String> replaces();

    /**
     * The version value that should be used in the generated CSV instead of the automatically detected one extracted from the
     * project information.
     */
    Optional<String> version();

}
