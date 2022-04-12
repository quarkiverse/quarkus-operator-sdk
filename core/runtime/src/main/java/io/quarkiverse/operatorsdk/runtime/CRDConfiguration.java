package io.quarkiverse.operatorsdk.runtime;

import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.client.CustomResource;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

@ConfigGroup
public class CRDConfiguration {

    public static final String DEFAULT_OUTPUT_DIRECTORY = "kubernetes";
    public static final String DEFAULT_VALIDATE = "true";
    public static final String DEFAULT_VERSIONS = "v1";
    /**
     * Whether the operator should check that the CRD is properly deployed and that the associated
     * {@link CustomResource} implementation matches its information before registering the associated
     * controller.
     */
    @ConfigItem(defaultValue = DEFAULT_VALIDATE)
    public Boolean validate;

    /**
     * Whether the extension should automatically generate the CRD based on {@link CustomResource}
     * implementations.
     */
    @ConfigItem
    public Optional<Boolean> generate;

    /**
     * Whether the extension should automatically apply updated CRDs when they change.
     * When running on DEV mode, the CRD changes will always be applied automatically.
     */
    @ConfigItem
    public Optional<Boolean> apply;

    /**
     * Comma-separated list of which CRD versions should be generated.
     */
    @ConfigItem(defaultValue = DEFAULT_VERSIONS)
    public List<String> versions;

    /**
     * The directory where the CRDs will be generated, relative to the project's output directory.
     */
    @ConfigItem(defaultValue = DEFAULT_OUTPUT_DIRECTORY)
    public String outputDirectory;
}
