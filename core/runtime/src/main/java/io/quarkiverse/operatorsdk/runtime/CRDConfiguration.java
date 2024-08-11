package io.quarkiverse.operatorsdk.runtime;

import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.client.CustomResource;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface CRDConfiguration {

    String DEFAULT_OUTPUT_DIRECTORY = "kubernetes";
    String DEFAULT_VALIDATE = "true";
    String DEFAULT_VERSIONS = "v1";

    /**
     * Whether the operator should check that the CRD is properly deployed and that the associated
     * {@link CustomResource} implementation matches its information before registering the associated
     * controller.
     */
    @WithDefault(DEFAULT_VALIDATE)
    Boolean validate();

    /**
     * Whether the extension should automatically generate the CRD based on {@link CustomResource}
     * implementations.
     */
    Optional<Boolean> generate();

    /**
     * Whether the extension should automatically apply updated CRDs when they change.
     * When running on DEV mode, the CRD changes will always be applied automatically.
     */
    Optional<Boolean> apply();

    /**
     * Comma-separated list of which CRD versions should be generated.
     */
    @WithDefault(DEFAULT_VERSIONS)
    List<String> versions();

    /**
     * The directory where the CRDs will be generated, defaults to the {@code kubernetes}
     * directory of the project's output directory.
     */
    Optional<String> outputDirectory();

    /**
     * Whether the extension should generate all CRDs even if some are not tied to a Reconciler.
     */
    @WithDefault("false")
    Boolean generateAll();

    /**
     * Whether the CRDs should be generated in parallel. Please note that this feature is experimental
     * and it may lead to unexpected results.
     */
    @WithDefault("false")
    Boolean generateInParallel();

    /**
     * A comma-separated list of fully-qualified class names implementing custom resources to exclude from the CRD generation
     * process.
     */
    Optional<List<String>> excludeResources();
}
