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
    @ConfigItem(defaultValue = "true")
    @Deprecated
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

}
