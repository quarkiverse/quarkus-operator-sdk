/**
 * Copyright 2021 Red Hat, Inc. and/or its affiliates.
 *
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.quarkiverse.operatorsdk.runtime;

import static io.quarkiverse.operatorsdk.runtime.Constants.QOSDK_USE_BUILDTIME_NAMESPACES;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

@ConfigRoot(name = "operator-sdk", phase = ConfigPhase.RUN_TIME)
public class RunTimeOperatorConfiguration {

    /**
     * Maps a controller name to its configuration.
     */
    @ConfigItem
    public Map<String, RunTimeControllerConfiguration> controllers;

    /**
     * The max number of concurrent dispatches of reconciliation requests to controllers.
     */
    @ConfigItem
    public Optional<Integer> concurrentReconciliationThreads;

    /**
     * Amount of seconds the SDK waits for reconciliation threads to terminate before shutting down.
     */
    @ConfigItem
    public Optional<Integer> terminationTimeoutSeconds;

    /**
     * An optional list of comma-separated namespace names all controllers will watch if they do not specify their own list. If
     * a controller specifies its own list either via the
     * {@link io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration} annotation or via the associated
     * {@code application.properties} property, that value will be used instead of the operator-level default value that this
     * configuration option provides.
     *
     * <p>
     * If this property is left empty then controllers will watch all namespaces by default (which is equivalent to setting this
     * property to {@link Constants#WATCH_ALL_NAMESPACES}, assuming they do not provide their own list of namespaces to watch. .
     * The value can be set to {@link Constants#WATCH_CURRENT_NAMESPACE} to make all controllers watch the current namespace as
     * specified by the kube config file the operator uses.
     * </p>
     * <p>
     * A default value is defined so that we are able to detect whether the configuration value is set to {@code ""} (empty
     * string) as setting the value to the empty string will result in resetting the configuration and an empty
     * {@link Optional}, while not setting the value at all will result in the default value being applied.
     * </p>
     */
    @ConfigItem(defaultValue = QOSDK_USE_BUILDTIME_NAMESPACES)
    public Optional<List<String>> namespaces;

    /**
     * The max number of concurrent workflow processing requests.
     */
    @ConfigItem
    public Optional<Integer> concurrentWorkflowThreads;

    /**
     * How long the operator will wait for informers to finish synchronizing their caches on startup
     * before timing out.
     */
    @ConfigItem(defaultValue = "2M")
    public Duration cacheSyncTimeout;
}
