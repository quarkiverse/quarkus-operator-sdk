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
package io.quarkiverse.operatorsdk.samples.pingpong;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_CURRENT_NAMESPACE;

import jakarta.inject.Inject;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.quarkiverse.operatorsdk.annotations.CSVMetadata;

@CSVMetadata(bundleName = "pingpong-operator")
@ControllerConfiguration(namespaces = WATCH_CURRENT_NAMESPACE)
@SuppressWarnings("unused")
public class PingReconciler implements Reconciler<Ping> {

    @Inject
    KubernetesClient client;

    @Override
    public UpdateControl<Ping> reconcile(Ping ping, Context<Ping> context) {
        Status status = ping.getStatus();
        if (status != null && Status.State.PROCESSED == status.getState()) {
            return UpdateControl.noUpdate();
        }

        final String expectedPongResource = ping.getMetadata().getName() + "-pong";
        final var pongs = client.resources(Pong.class);
        final var existing = pongs.withName(expectedPongResource).get();
        if (existing == null) {
            Pong pong = new Pong();
            pong.getMetadata().setName(expectedPongResource);
            pongs.resource(pong).create();
        }

        ping.setStatus(new Status(Status.State.PROCESSED));
        return UpdateControl.updateStatus(ping);
    }
}
