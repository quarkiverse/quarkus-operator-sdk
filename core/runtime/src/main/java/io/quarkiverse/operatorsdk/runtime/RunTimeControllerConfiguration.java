package io.quarkiverse.operatorsdk.runtime;

import static io.quarkiverse.operatorsdk.runtime.Constants.QOSDK_USE_BUILDTIME_NAMESPACES;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.MaxReconciliationInterval;
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
    @WithDefault(QOSDK_USE_BUILDTIME_NAMESPACES)
    Optional<List<String>> namespaces();

    /**
     * The optional name of the finalizer for the controller. If none is provided, one will be
     * automatically generated.
     */
    Optional<String> finalizer();

    /**
     * Configures the controller's {@link io.javaoperatorsdk.operator.processing.retry.GradualRetry} policy. This will only take
     * effect if {@link ControllerConfiguration#retry()} is set to use the
     * {@link io.javaoperatorsdk.operator.processing.retry.GenericRetry} implementation (which is what is used by default if not
     * otherwise configured)
     */
    ExternalGradualRetryConfiguration retry();

    /**
     * An optional list of comma-separated label selectors that Custom Resources must match to trigger the controller.
     * See <a href="https://kubernetes.io/docs/concepts/overview/working-with-objects/labels/">...</a> for more details on
     * selectors.
     */
    Optional<String> selector();

    /**
     * An optional {@link Duration} to specify the maximum time that is allowed to elapse before a reconciliation will happen
     * regardless of the presence of events. See {@link MaxReconciliationInterval#interval()} for more details.
     * Value is specified according to the rules defined at {@link Duration#parse(CharSequence)}.
     */
    Optional<Duration> maxReconciliationInterval();
}
