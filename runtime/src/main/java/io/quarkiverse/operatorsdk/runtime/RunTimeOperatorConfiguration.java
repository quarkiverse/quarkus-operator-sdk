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
     */
    @ConfigItem
    public Optional<List<String>> namespaces;

    /**
     * The optional name of the finalizer to use for controllers. If none is provided, one will be
     * automatically generated. It should be noted that having several controllers use the same finalizer might
     * create issues and this configuration item is mostly useful when we don't want to use finalizers at all by
     * default (using the {@link io.javaoperatorsdk.operator.api.Controller#NO_FINALIZER} value). Sets the default value for all
     * controllers.
     */
    @ConfigItem
    public Optional<String> finalizer;
}
