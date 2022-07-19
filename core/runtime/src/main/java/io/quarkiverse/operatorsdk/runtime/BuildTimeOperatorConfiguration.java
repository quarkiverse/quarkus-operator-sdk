package io.quarkiverse.operatorsdk.runtime;

import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

@ConfigRoot(name = "operator-sdk", phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public class BuildTimeOperatorConfiguration {

    /**
     * Maps a controller name to its configuration.
     */
    @ConfigItem
    public Map<String, BuildTimeControllerConfiguration> controllers;

    /**
     * The optional CRD-related configuration options
     */
    @ConfigItem
    public CRDConfiguration crd;

    /**
     * Whether controllers should only process events if the associated resource generation has
     * increased since last reconciliation, otherwise will process all events. Sets the default value for all controllers.
     */
    @ConfigItem(defaultValue = "true")
    public Optional<Boolean> generationAware;


    /**
     * Whether Role-Based Access Control (RBAC) resources should be generated in the kubernetes manifests.
     */
    @ConfigItem(defaultValue = "false")
    public Boolean disableRbacGeneration;

    /**
     * Whether the operator should be automatically started or not. Mostly useful for testing scenarios.
     */
    @ConfigItem
    public Optional<Boolean> startOperator;
}
