package io.quarkiverse.operatorsdk.runtime;

import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

@ConfigGroup
public class BuildTimeControllerConfiguration {

    /**
     * Whether the controller should only process events if the associated resource generation has
     * increased since last reconciliation, otherwise will process all events.
     */
    @ConfigItem
    public Optional<Boolean> generationAware;

    /**
     * An optional list of comma-separated watched namespace names that will be used to generate manifests at build time.
     *
     * <p>
     * Note that this is provided as a means to quickly deploy a specific controller to test it by applying the generated
     * manifests to the target cluster. If empty, no manifests will be generated. The namespace in which the controller will be
     * deployed will be the currently configured namespace as specified by your {@code .kube/config} file, unless you specify
     * the target deployment namespace using the {@code quarkus.kubernetes.namespace} property.
     * </p>
     *
     * <p>
     * As this functionality cannot handle namespaces that are not know until runtime (because the generation happens during
     * build time), we recommend that you use a different mechanism such as OLM or Helm charts to deploy your operator in
     * production.
     * </p>
     *
     * <p>
     * This replaces the previous {@code namespaces} property which was confusing and against Quarkus best practices since it
     * existed both at build time and runtime. That property wasn't also adequately capturing the fact that namespaces that
     * wouldn't be known until runtime would render whatever got generated at build time invalid as far as generated manifests
     * were concerned.
     * </p>
     */
    @ConfigItem
    public Optional<List<String>> generateWithWatchedNamespaces;
}
