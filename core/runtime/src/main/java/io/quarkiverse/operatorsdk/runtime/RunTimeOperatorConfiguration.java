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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
     * An optional list of comma-separated namespace names all controllers will watch if not specified. If this
     * property is left empty then controllers will watch all namespaces by default. Sets the default value for all controllers.
     * The value can be set to "JOSDK_WATCH_CURRENT" to watch the current (default) namespace from kube config.
     * Constant(s) can be found in at {@link io.javaoperatorsdk.operator.api.reconciler.Constants}".
     */
    @ConfigItem
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
