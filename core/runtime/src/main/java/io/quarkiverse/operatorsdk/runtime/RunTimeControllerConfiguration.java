package io.quarkiverse.operatorsdk.runtime;

import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface RunTimeControllerConfiguration {

    /**
     * An optional list of comma-separated namespace names the controller should watch. If this
     * property is left empty then the controller will watch all namespaces.
     * The value can be set to "JOSDK_WATCH_CURRENT" to watch the current (default) namespace from kube config.
     * Constant(s) can be found in at {@link io.javaoperatorsdk.operator.api.reconciler.Constants}".
     */
    @WithDefault(Constants.QOSDK_USE_BUILDTIME_NAMESPACES)
    Optional<List<String>> namespaces();

    /**
     * The optional name of the finalizer for the controller. If none is provided, one will be
     * automatically generated.
     */
    Optional<String> finalizer();

    /**
     * The optional controller retry configuration
     */
    ExternalRetryConfiguration retry();

    /**
     * An optional list of comma-separated label selectors that Custom Resources must match to trigger the controller.
     * See <a href="https://kubernetes.io/docs/concepts/overview/working-with-objects/labels/">the documentation</a> for more
     * details on selectors.
     */
    Optional<String> selector();
}
