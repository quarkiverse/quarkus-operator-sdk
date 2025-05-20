package io.quarkiverse.operatorsdk.runtime;

import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.client.CustomResource;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigGroup
public interface CRDConfiguration {

    String DEFAULT_OUTPUT_DIRECTORY = "kubernetes";
    String DEFAULT_VALIDATE = "true";
    String DEFAULT_VERSIONS = "v1";
    String DEFAULT_USE_V1_CRD_GENERATOR = "false";

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
     * <p>
     * <strong>NOTE that this option is only considered when *not* in production mode as applying the CRD to a production
     * cluster could be dangerous if done automatically.</strong>
     * </p>
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
     * Whether the extension should generate all CRDs for Custom Resource implementations known to the application even if some
     * are not tied to a Reconciler.
     */
    @WithDefault("false")
    Boolean generateAll();

    /**
     * Whether the CRDs should be generated in parallel.
     */
    @WithDefault("false")
    Boolean generateInParallel();

    /**
     * A comma-separated list of fully-qualified class names implementing {@link CustomResource} to exclude from the CRD
     * generation process.
     */
    Optional<List<String>> excludeResources();

    /**
     * A comma-separated list of paths where external CRDs that need to be referenced for non-generated custom resources.
     * Typical use cases where this might be needed include when custom resource implementations are located in a different
     * module than the controller implementation or when the CRDs are not generated at all (e.g. in integration cases where your
     * operator needs to deal with 3rd party custom resources).
     *
     * <p>
     * Paths can be either absolute or relative, in which case they will be resolved from the current module root directory.
     * </p>
     *
     * @since 6.8.4
     */
    Optional<List<String>> externalCRDLocations();

    /**
     * Whether or not to use the v1 version of the CRD generator. Note that this should only be used if a compatibility issue is
     * found with the v2 generator, which is the default one and the one that is actively maintained.
     *
     * @return {@code true} if the v1 version of the CRD generator should be used
     * @since 6.8.5
     * @deprecated using this method should be reserved for blocking situations, please open an issue reporting the problem
     *             you're facing with the v2 generator before reverting to use the v1 version
     */
    @Deprecated(forRemoval = true)
    @WithDefault(DEFAULT_USE_V1_CRD_GENERATOR)
    @WithName("use-deprecated-v1-crd-generator")
    Boolean useV1CRDGenerator();

    /**
     * The fully qualified name of a Fabric8 CRD generator v2 API {@code CRDPostProcessor} implementation, providing a public,
     * no-arg constructor for instantiation
     *
     * @since 7.2.0
     */
    @WithName("post-processor")
    Optional<String> crdPostProcessorClass();
}
