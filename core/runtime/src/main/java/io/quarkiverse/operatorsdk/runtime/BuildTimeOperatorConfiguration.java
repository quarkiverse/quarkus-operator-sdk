package io.quarkiverse.operatorsdk.runtime;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "quarkus.operator-sdk")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface BuildTimeOperatorConfiguration {

    /**
     * Maps a controller name to its configuration.
     */
    Map<String, BuildTimeControllerConfiguration> controllers();

    /**
     * The optional CRD-related configuration options
     */
    CRDConfiguration crd();

    /**
     * Whether controllers should only process events if the associated resource generation has
     * increased since last reconciliation, otherwise will process all events. Sets the default value
     * for all controllers.
     */
    @WithDefault("true")
    Optional<Boolean> generationAware();

    /**
     * Whether Role-Based Access Control (RBAC) resources generated by the Kubernetes extension should be augmented by this
     * extension.
     */
    @WithDefault("false")
    Boolean disableRbacGeneration();

    /**
     * Whether the operator should be automatically started or not. Mostly useful for testing
     * scenarios.
     */
    @WithDefault("true")
    Boolean startOperator();

    /**
     * Whether the injected Kubernetes client should be stopped when the operator is stopped.
     */
    @WithDefault("true")
    Boolean closeClientOnStop();

    /**
     * Whether the operator should stop if an informer error (such as one caused by missing / improper
     * RBACs) occurs during startup.
     */
    @WithDefault("true")
    Boolean stopOnInformerErrorDuringStartup();

    /**
     * Whether to fail or emit a debug-level (warning-level when misalignment is at the minor or above version level) log when
     * the extension detects that there are misaligned versions.
     * <p/>
     * The following versions are checked for alignment:
     * <ul>
     * <li>declared Quarkus version used to build the extension vs. actually used Quarkus version at runtime</li>
     * <li>Fabric8 client version used by JOSDK vs. actually used Fabric8 client version</li>
     * <li>Fabric8 client version used by Quarkus vs. actually used Fabric8 client version</li>
     * </ul>
     */
    @WithDefault("false")
    Boolean failOnVersionCheck();

    /**
     * The list of profile names for which leader election should be activated. This is mostly useful for testing scenarios
     * where leader election behavior might lead to issues.
     */
    @WithDefault("prod")
    List<String> activateLeaderElectionForProfiles();

    /**
     * The optional Server-Side Apply (SSA) related configuration.
     */
    @WithName("enable-ssa")
    @WithDefault("true")
    boolean enableSSA();

    /**
     * An optional list of comma-separated watched namespace names that will be used to generate manifests at build time if
     * controllers do <strong>NOT</strong> specify a value individually. See
     * {@link BuildTimeControllerConfiguration#generateWithWatchedNamespaces} for more information.
     */
    Optional<List<String>> generateWithWatchedNamespaces();

    /**
     * Helm Chart related configurations.
     */
    HelmConfiguration helm();

    default boolean isControllerOwningPrimary(String controllerName) {
        final var controllerConfiguration = controllers().get(controllerName);
        return controllerConfiguration == null || !controllerConfiguration.unownedPrimary();
    }
}
