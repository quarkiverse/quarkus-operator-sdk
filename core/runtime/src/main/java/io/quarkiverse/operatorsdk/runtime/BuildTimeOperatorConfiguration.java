package io.quarkiverse.operatorsdk.runtime;

import java.util.Map;
import java.util.Optional;

import io.fabric8.kubernetes.client.CustomResource;
import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

@ConfigRoot(name = "operator-sdk", phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public class BuildTimeOperatorConfiguration {

    /**
     * Whether the operator should check that the CRD is properly deployed and that the associated
     * {@link CustomResource} implementation matches its information before registering the associated
     * controller.
     *
     * @deprecated Use {@link CRDConfiguration#validate} instead
     */
    @ConfigItem
    @Deprecated(forRemoval = true)
    public Optional<Boolean> checkCRDAndValidateLocalModel;

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
     * The optional fully qualified name of a CDI event class that controllers will wait for before
     * registering with the Operator. Sets the default value for all controllers.
     */
    @ConfigItem
    public Optional<String> delayRegistrationUntilEvent;

    /**
     * Whether Role-Based Access Control (RBAC) resources should be generated in the kubernetes manifests.
     */
    @ConfigItem(defaultValue = "false")
    public Boolean disableRbacGeneration;

    /**
     * Whether the operator should be automatically started or not. Mostly useful for testing scenarios.
     */
    @ConfigItem(defaultValue = "true")
    public Boolean startOperator;
}
