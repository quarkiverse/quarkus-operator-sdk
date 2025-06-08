package io.quarkiverse.operatorsdk.runtime.api;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfigurationOverrider;

/**
 * Implement to change a {@link io.javaoperatorsdk.operator.api.reconciler.Reconciler}'s
 * configuration at runtime
 *
 * @param <P> the primary resource type of the reconciler
 * @since 7.2.0
 */
public interface ConfigurableReconciler<P extends HasMetadata> {
    /**
     * Updates the reconciler's configuration by applying the modifications specified by the provided
     * {@link ControllerConfigurationOverrider}. Note that the resulting configuration update won't be recorded by the
     * {@link ConfigurationService} as this currently is a JOSDK limitation. To access the up-to-date configuration, you need to
     * retrieve it from the associated {@link io.javaoperatorsdk.operator.RegisteredController} from
     * {@link io.javaoperatorsdk.operator.RuntimeInfo}.
     *
     * @param configOverrider provides the modifications to apply to the existing reconciler's
     *        configuration
     */
    void updateConfigurationFrom(ControllerConfigurationOverrider<P> configOverrider);
}
