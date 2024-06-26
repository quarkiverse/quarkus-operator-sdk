package io.quarkiverse.operatorsdk.runtime;

import static io.quarkiverse.operatorsdk.runtime.Constants.QOSDK_USE_BUILDTIME_NAMESPACES;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import io.javaoperatorsdk.operator.api.reconciler.MaxReconciliationInterval;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

@ConfigGroup
public class RunTimeControllerConfiguration {

    /**
     * An optional list of comma-separated namespace names the controller should watch. If this
     * property is left empty then the controller will watch all namespaces.
     * The value can be set to "JOSDK_WATCH_CURRENT" to watch the current (default) namespace from kube config.
     * Constant(s) can be found in at {@link io.javaoperatorsdk.operator.api.reconciler.Constants}".
     */
    @ConfigItem(defaultValue = QOSDK_USE_BUILDTIME_NAMESPACES)
    public Optional<List<String>> namespaces;

    /**
     * The optional name of the finalizer for the controller. If none is provided, one will be
     * automatically generated.
     */
    @ConfigItem
    public Optional<String> finalizer;

    /**
     * The optional controller retry configuration
     */
    @ConfigItem
    public ExternalRetryConfiguration retry;

    /**
     * An optional list of comma-separated label selectors that Custom Resources must match to trigger the controller.
     * See <a href="https://kubernetes.io/docs/concepts/overview/working-with-objects/labels/">...</a> for more details on
     * selectors.
     */
    @ConfigItem
    public Optional<String> selector;

    /**
     * An optional {@link Duration} to specify the maximum time that is allowed to elapse before a reconciliation will happen
     * regardless of the presence of events. See {@link MaxReconciliationInterval#interval()} for more details.
     * Value is specified according to the rules defined at {@link Duration#parse(CharSequence)}.
     */
    @ConfigItem
    public Optional<Duration> maxReconciliationInterval;
}
