package io.quarkiverse.operatorsdk.deployment;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.quarkiverse.operatorsdk.runtime.QuarkusControllerConfiguration;

@SuppressWarnings("rawtypes")
class ContextStoredControllerConfigurations {
    private final Map<String, QuarkusControllerConfiguration> configurations = new HashMap<>();

    Map<String, QuarkusControllerConfiguration> getConfigurations() {
        return Collections.unmodifiableMap(configurations);
    }

    void recordConfiguration(QuarkusControllerConfiguration configuration) {
        // if we get passed null, assume that it's because the configuration had already been generated and therefore doesn't need to be recorded again
        if (configuration != null) {
            configurations.put(configuration.getAssociatedReconcilerClassName(), configuration);
        }
    }

    // @formatter:off
    /**
     * Theoretically, a configuration needs to be regenerated if:
     * <ul>
     *   <li>the {@link io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration} annotation has changed</li>
     *   <li>the class associated with the primary resource has changed</li>
     *   <li>any of the dependent resources associated with out reconciler has changed</li>
     *   <li>the configuration properties have changed as follows:
     *      <ul>
     *        <li>extension-wide properties affecting all controllers have changed</li>
     *        <li>controller-specific properties have changed</li>
     *      </ul>
     *   </li>
     * </ul>
     *
     * Here, perform a simplified check and we request regeneration of the configuration if:
     * <ul>
     *   <li>the Reconciler class has changed</li>
     *   <li>the primary resource class has changed</li>
     *   <li>{@code application.properties} as a whole has changed</li>
     *   <li>any of the declared {@link io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource} classes have changed</li>
     * </ul>
     *
     * This could, of course, be further optimized if needed.
     *
     * @param reconcilerClassName the class name associated with the Reconciler for which
     *                            configuration generation is considered
     * @param changedClasses the set of changed class names
     * @param changedResources the set of changed resource names
     * @return the configuration associated with the specified reconciler or {@code null} if it doesn't already exist or needs to be regenerated
     */
    // @formatter:on
    public QuarkusControllerConfiguration<?> configurationOrNullIfNeedGeneration(String reconcilerClassName,
            Set<String> changedClasses, Set<String> changedResources) {
        QuarkusControllerConfiguration<?> configuration = configurations.get(reconcilerClassName);
        return configuration != null &&
                shouldRegenerate(reconcilerClassName, changedClasses, changedResources, configuration) ? null : configuration;
    }

    private boolean shouldRegenerate(String reconcilerClassName, Set<String> changedClasses,
            Set<String> changedResources, QuarkusControllerConfiguration<?> configuration) {
        return changedClasses.contains(reconcilerClassName)
                || changedClasses.contains(configuration.getResourceTypeName())
                || changedResources.contains("application.properties")
                || configuration.areDependentsImpactedBy(changedClasses);
    }
}
